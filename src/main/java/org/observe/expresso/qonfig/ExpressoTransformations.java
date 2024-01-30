package org.observe.expresso.qonfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

import org.observe.Equivalence;
import org.observe.Observable;
import org.observe.ObservableAction;
import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.Transformation;
import org.observe.Transformation.MaybeReversibleTransformation;
import org.observe.Transformation.ReversibleTransformationBuilder;
import org.observe.Transformation.TransformReverse;
import org.observe.Transformation.TransformationValues;
import org.observe.assoc.ObservableMap;
import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableCollection.CollectionDataFlow;
import org.observe.expresso.CompiledExpressoEnv;
import org.observe.expresso.ExpressoCompilationException;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.InterpretedExpressoEnv;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelType;
import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableExpression;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelComponentId;
import org.observe.expresso.ObservableModelSet.ModelInstantiator;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ModelValueInstantiator;
import org.observe.expresso.VariableType;
import org.observe.expresso.ops.NameExpression;
import org.observe.expresso.qonfig.ExElement.Def;
import org.observe.expresso.qonfig.ExElement.Void;
import org.observe.expresso.qonfig.ExpressoTransformations.CombineWith.TransformationModification;
import org.observe.util.TypeTokens;
import org.qommons.LambdaUtils;
import org.qommons.Named;
import org.qommons.TriFunction;
import org.qommons.collect.BetterList;
import org.qommons.config.AbstractQIS;
import org.qommons.config.QonfigAddOn;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.config.QonfigInterpreterCore;

import com.google.common.reflect.TypeToken;

public class ExpressoTransformations {
	public static final String TARGET_MODEL_TYPE_KEY = "Expresso.Transformation.Target.Model.Type";

	private ExpressoTransformations() {
	}

	@ExElementTraceable(toolkit = ExpressoBaseV0_1.BASE,
		qonfigType = "transform",
		interpretation = ExpressoTransformedElement.Interpreted.class)
	public static class ExpressoTransformedElement<M1, M2> extends ExElement.Def.Abstract<ModelValueElement<?>>
	implements ModelValueElement.CompiledSynth<M2, ModelValueElement<?>> {
		private String theModelPath;
		private CompiledExpression theSource;
		private final List<Operation<?, ?, ?>> theOperations;
		private boolean isPrepared;

		public ExpressoTransformedElement(Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType);
			theOperations = new ArrayList<>();
		}

		@Override
		public String getModelPath() {
			return theModelPath;
		}

		@Override
		public ModelType<M2> getModelType(CompiledExpressoEnv env) {
			return (ModelType<M2>) theOperations.get(theOperations.size() - 1).getTargetModelType();
		}

		@QonfigAttributeGetter("source")
		public CompiledExpression getSource() {
			return theSource;
		}

		@QonfigChildGetter("op")
		public List<Operation<?, ?, ?>> getOperations() {
			return Collections.unmodifiableList(theOperations);
		}

		@Override
		public CompiledExpression getElementValue() {
			return null;
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			super.doUpdate(session);
			theModelPath = session.get(ModelValueElement.PATH_KEY, String.class);
			theSource = getAttributeExpression("source", session);
			isPrepared = false;
		}

		@Override
		public void prepareModelValue(ExpressoQIS session) throws QonfigInterpretationException {
			if (isPrepared)
				return;
			isPrepared = true;
			ModelType<?> modelType;
			try {
				modelType = theSource.getModelType();
			} catch (ExpressoCompilationException e) {
				throw new QonfigInterpretationException(e.getMessage(), e.getPosition(), e.getErrorLength(), e);
			}
			int i = 0;
			for (ExpressoQIS op : session.forChildren("op")) {
				@SuppressWarnings("rawtypes")
				Class<? extends Operation> transformType = getTransformFor(modelType);
				if (transformType == null) {
					throw new QonfigInterpretationException("No transform supported for model type " + modelType,
						op.getElement().getPositionInFile(), 0);
				}
				Operation<?, ?, ?> next;
				if (i < theOperations.size()) {
					if (ExElement.typesEqual(theOperations.get(i).getElement(), op.getElement()))
						next = theOperations.get(i);
					else {
						next = null;
						theOperations.subList(i, theOperations.size()).clear();
					}
				} else
					next = null;
				if (next == null) {
					op = op.asElement(op.getElement().getType());
					if (op.getInterpretationSupport(transformType) == null) {
						throw new QonfigInterpretationException(
							"No transform supported for operation type " + op.getFocusType().getName() + " for model type " + modelType,
							op.getElement().getPositionInFile(), 0);
					}
					try {
						op.put(TARGET_MODEL_TYPE_KEY, modelType);
						next = op.interpret(transformType);
						theOperations.add(next);
					} catch (RuntimeException e) {
						throw new QonfigInterpretationException("Could not interpret operation " + op.toString()
						+ " as a transformation from " + modelType + " via " + transformType.getName(),
						op.getElement().getPositionInFile(), 0, e.getCause());
					}
				}
				((Operation<Object, ?, ?>) next).update(op, (ModelType<Object>) modelType);

				modelType = next.getTargetModelType();
				i++;
			}
		}

		/**
		 * Gets the transformation type for an observable model type. This will be used to query {@link AbstractQIS#interpret(Class)} to
		 * satisfy a transformation operation.
		 *
		 * @param modelType The model type to get the transform type for
		 * @return The type of transformation known to be able to handle observable structures of the given model type
		 */
		protected <M> Class<? extends Operation<M, ?, ?>> getTransformFor(ModelType<M> modelType) {
			if (modelType == ModelTypes.Event)
				return (Class<? extends Operation<M, ?, ?>>) ObservableTransform.class;
			else if (modelType == ModelTypes.Action)
				return (Class<? extends Operation<M, ?, ?>>) ActionTransform.class;
			else if (modelType == ModelTypes.Value)
				return (Class<? extends Operation<M, ?, ?>>) ValueTransform.class;
			else if (modelType == ModelTypes.Collection || modelType == ModelTypes.Set || modelType == ModelTypes.SortedCollection
				|| modelType == ModelTypes.SortedSet)
				return (Class<? extends Operation<M, ?, ?>>) CollectionTransform.class;
			else
				return null;
		}

		@Override
		public Interpreted<M1, ?, M2, ?> interpretValue(ExElement.Interpreted<?> parent) {
			return new Interpreted<>(this, parent);
		}

		public static class Interpreted<M1, MV1 extends M1, M2, MV2 extends M2>
		extends ExElement.Interpreted.Abstract<ModelValueElement<MV2>>
		implements ModelValueElement.InterpretedSynth<M2, MV2, ModelValueElement<MV2>> {
			private InterpretedValueSynth<M1, MV1> theSource;
			private final List<Operation.Interpreted<?, ?, ?, ?, ?>> theOperations;

			public Interpreted(ExpressoTransformedElement<M1, M2> definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
				theOperations = new ArrayList<>();
			}

			@Override
			public ExpressoTransformedElement<M1, M2> getDefinition() {
				return (ExpressoTransformedElement<M1, M2>) super.getDefinition();
			}

			public InterpretedValueSynth<M1, MV1> getSource() {
				return theSource;
			}

			public List<Operation.Interpreted<?, ?, ?, ?, ?>> getOperations() {
				return Collections.unmodifiableList(theOperations);
			}

			@Override
			public InterpretedValueSynth<?, ?> getElementValue() {
				return null;
			}

			@Override
			public Interpreted<M1, MV1, M2, MV2> setParentElement(ExElement.Interpreted<?> parent) {
				super.setParentElement(parent);
				return this;
			}

			@Override
			public void updateValue(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
				update(env);
			}

			@Override
			protected void doUpdate(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
				super.doUpdate(env);
				try {
					theSource = interpret(getDefinition().getSource(),
						((ModelType<M1>) getDefinition().getSource().getModelType()).<MV1> anyAs());
				} catch (ExpressoCompilationException e) {
					throw new ExpressoInterpretationException(e.getMessage(), e.getPosition(), e.getErrorLength(), e);
				}

				ModelInstanceType<?, ?> sourceType = theSource.getType();
				int i = 0;
				for (Operation<?, ?, ?> op : getDefinition().getOperations()) {
					Operation.Interpreted<?, ?, ?, ?, ?> interpOp;
					if (i < theOperations.size()) {
						if (theOperations.get(i).getIdentity() == op.getIdentity()) {
							interpOp = theOperations.get(i);
						} else {
							interpOp = null;
							theOperations.subList(i, theOperations.size()).clear();
						}
					} else
						interpOp = null;
					if (interpOp == null) {
						interpOp = op.interpret(this);
						theOperations.add(interpOp);
					}
					((Operation.Interpreted<Object, Object, ?, ?, ?>) interpOp).update((ModelInstanceType<Object, Object>) sourceType,
						getExpressoEnv());
					sourceType = interpOp.getTargetType();
					i++;
				}
			}

			@Override
			public List<? extends InterpretedValueSynth<?, ?>> getComponents() {
				return BetterList
					.of(Stream.concat(Stream.of(theSource), theOperations.stream().flatMap(op -> op.getComponents().stream())));
			}

			@Override
			public ModelInstanceType<M2, MV2> getType() {
				return (ModelInstanceType<M2, MV2>) theOperations.get(theOperations.size() - 1).getTargetType();
			}

			@Override
			public ModelValueElement<MV2> instantiate() throws ModelInstantiationException {
				return new Instantiator<>(this);
			}

			@Override
			public ModelValueElement<MV2> create() {
				return null;
			}
		}

		static class Instantiator<MV1, MV2> extends ModelValueElement.Abstract<MV2> {
			private final ModelValueInstantiator<MV1> theSource;
			private final List<Operation.Instantiator<?, ?>> theOperations;
			private final boolean isEfficientCopy;
			private final TransformInstantiator<MV1, MV2> theFullTransform;

			public Instantiator(ExpressoTransformedElement.Interpreted<?, MV1, ?, MV2> interpreted) throws ModelInstantiationException {
				super(interpreted);
				theSource = interpreted.getSource().instantiate();
				List<Operation.Instantiator<?, ?>> operations = new ArrayList<>(interpreted.getOperations().size());
				boolean efficientCopy = true;
				TransformInstantiator<MV1, ?> fullTransform = TransformInstantiator.unity();
				for (Operation.Interpreted<?, ?, ?, ?, ?> op : interpreted.getOperations()) {
					Operation.Instantiator<?, ?> opInst = op.instantiate();
					operations.add(opInst);
					if (efficientCopy)
						efficientCopy = opInst instanceof Operation.EfficientCopyingInstantiator
						&& ((Operation.EfficientCopyingInstantiator<?, ?>) opInst).isEfficientCopy();
					fullTransform = ((Operation.Instantiator<Object, ?>) opInst).after((TransformInstantiator<MV1, Object>) fullTransform);
				}
				theOperations = operations;
				isEfficientCopy = efficientCopy;
				theFullTransform = (TransformInstantiator<MV1, MV2>) fullTransform;
			}

			@Override
			public void instantiate() throws ModelInstantiationException {
				theSource.instantiate();
				for (Operation.Instantiator<?, ?> op : theOperations)
					op.instantiate();
			}

			@Override
			public MV2 get(ModelSetInstance models) throws ModelInstantiationException, IllegalStateException {
				MV1 sourceValue = theSource.get(models);
				return theFullTransform.transform(sourceValue, models);
			}

			@Override
			public MV2 forModelCopy(MV2 value, ModelSetInstance sourceModels, ModelSetInstance newModels)
				throws ModelInstantiationException {
				if (isEfficientCopy) {
					Object[] chain = new Object[theOperations.size() + 1];
					chain[theOperations.size()] = value;
					Object target = value;
					for (int i = theOperations.size() - 1; i >= 0; i--) {
						chain[i] = ((Operation.EfficientCopyingInstantiator<?, Object>) theOperations.get(i)).getSource(target);
						target = chain[i];
					}
					target = chain[0];
					for (int i = 0; i < theOperations.size(); i++) {
						Object sourceValue = target;
						target = chain[i + 1];
						target = ((Operation.EfficientCopyingInstantiator<Object, Object>) theOperations.get(i)).forModelCopy(target,
							sourceValue, sourceModels, newModels);
					}

					return (MV2) target;
				} else {
					boolean different = false;
					for (Operation.Instantiator<?, ?> op : theOperations) {
						if (op.isDifferent(sourceModels, newModels)) {
							different = true;
							break;
						}
					}
					if (different)
						return get(newModels);
					else
						return value;
				}
			}
		}
	}

	/**
	 * A definition of a transformation operation. This compiled structure is capable of generating an interpreted structure
	 * ({@link Operation.Interpreted}) which is capable of transforming an actual model value into another (via
	 * {@link Operation.Interpreted#transform(Object, ModelSetInstance)}.
	 *
	 * @param <M1> The model type of the source value that this transformation accepts
	 * @param <M2> The model type of the target value that this transformation produces
	 * @param <E> The type of element that this definition produces
	 */
	public interface Operation<M1, M2, E extends ExElement> extends ExElement.Def<E> {
		/** @return The model type of the target value that this transformation produces */
		ModelType<? extends M2> getTargetModelType();

		void update(ExpressoQIS session, ModelType<M1> sourceModelType) throws QonfigInterpretationException;

		@Override
		default void update(ExpressoQIS session) throws QonfigInterpretationException {
			throw new QonfigInterpretationException("Use update(ExpressionQIS, ModelType<M1>) instead",
				reporting().getFileLocation().getPosition(0), 0);
		}

		/**
		 * @param sourceType The model type of the source value
		 * @return A transformer for transforming actual values
		 * @throws ExpressoInterpretationException If the transformer could not be produced
		 */
		Interpreted<M1, ?, M2, ?, ? extends E> interpret(ExElement.Interpreted<?> parent) throws ExpressoInterpretationException;

		/**
		 * The interpretation of an Operation
		 *
		 * @param <M1> The model type of the source observable
		 * @param <MV1> The type of the source observable
		 * @param <M2> The model type of the transformed observable
		 * @param <MV2> The type of the transformed observable
		 */
		public interface Interpreted<M1, MV1 extends M1, M2, MV2 extends M2, E extends ExElement> extends ExElement.Interpreted<E> {
			/**
			 * @param sourceType The type of the source values to transform
			 * @throws ExpressoInterpretationException If the transformer could not be interpreted
			 */
			void update(ModelInstanceType<M1, MV1> sourceType, InterpretedExpressoEnv env) throws ExpressoInterpretationException;

			/** @return The type of the transformed observable */
			ModelInstanceType<? extends M2, ? extends MV2> getTargetType();

			/** @return Any model values used by this transformation */
			BetterList<InterpretedValueSynth<?, ?>> getComponents();

			Instantiator<MV1, MV2> instantiate() throws ModelInstantiationException;
		}

		public interface Instantiator<MV1, MV2> extends TransformInstantiator<MV1, MV2> {
			void instantiate() throws ModelInstantiationException;

			/**
			 * Helps support the {@link ModelValueInstantiator#forModelCopy(Object, ModelSetInstance, ModelSetInstance)} method
			 *
			 * @param sourceModels The source model instance
			 * @param newModels The new model instance
			 * @return Whether observables produced by this reverse would be different between the two models
			 * @throws ModelInstantiationException If the inspection fails
			 */
			boolean isDifferent(ModelSetInstance sourceModels, ModelSetInstance newModels) throws ModelInstantiationException;
		}

		public interface EfficientCopyingInstantiator<MV1, MV2> extends Instantiator<MV1, MV2> {
			boolean isEfficientCopy();

			MV1 getSource(MV2 value);

			MV2 forModelCopy(MV2 prevValue, MV1 newSource, ModelSetInstance sourceModels, ModelSetInstance newModels)
				throws ModelInstantiationException;
		}
	}

	/**
	 * A transformer capable of handling a specific transformation of one observable structure to another
	 *
	 * @param <S> The type of the source observable
	 * @param <T> The type of the transformed observable
	 */
	public interface TransformInstantiator<S, T> {
		/**
		 * @param source The source observable
		 * @param models The models to use for the transformation
		 * @return The transformed observable
		 * @throws ModelInstantiationException If the transformation fails
		 */
		T transform(S source, ModelSetInstance models) throws ModelInstantiationException;

		default <S0> TransformInstantiator<S0, T> after(TransformInstantiator<S0, ? extends S> before) {
			return new DefaultCombined<>(before, this);
		}

		/**
		 * @param <T> The type of the transformer
		 * @return A transformer that does nothing, simply returning the value it's given
		 */
		static <T> TransformInstantiator<T, T> unity() {
			return (v, models) -> v;
		}

		/**
		 * Default {@link TransformInstantiator} implementation for the combination of two other transforms
		 *
		 * @param <S> The type of the source value given to the first component transform
		 * @param <I> The type of the source value given to the second component transform
		 * @param <T> The type of the value produced by the second transform (and this transform)
		 */
		public static class DefaultCombined<S, I, T> implements TransformInstantiator<S, T> {
			private final TransformInstantiator<S, ? extends I> theBefore;
			private final TransformInstantiator<I, T> theAfter;

			public DefaultCombined(TransformInstantiator<S, ? extends I> before, TransformInstantiator<I, T> after) {
				theBefore = before;
				theAfter = after;
			}

			@Override
			public T transform(S source, ModelSetInstance models) throws ModelInstantiationException {
				I intermediate = theBefore.transform(source, models);
				return theAfter.transform(intermediate, models);
			}
		}
	}

	/**
	 * A transformer capable of transforming an {@link Observable}
	 *
	 * @param <M> The model type of the target observable structure
	 * @param <E> The type of element produced
	 */
	public interface ObservableTransform<M, E extends ExElement> extends Operation<Observable<?>, M, E> {
	}

	/**
	 * A transformer capable of transforming an {@link ObservableAction}
	 *
	 * @param <M> The model type of the target observable structure
	 * @param <E> The type of element produced
	 */
	public interface ActionTransform<M, E extends ExElement> extends Operation<ObservableAction, M, E> {
	}

	/**
	 * A transformer capable of transforming a {@link SettableValue}
	 *
	 * @param <M> The model type of the target observable structure
	 * @param <E> The type of element produced
	 */
	public interface ValueTransform<M, E extends ExElement> extends Operation<SettableValue<?>, M, E> {
	}

	/**
	 * A transformer capable of transforming an {@link ObservableCollection}
	 *
	 * @param <M1> The model type of the source collection
	 * @param <M2> The model type of the target observable structure
	 * @param <E> The type of element produced
	 */
	public interface CollectionTransform<M1 extends ObservableCollection<?>, M2, E extends ExElement> extends Operation<M1, M2, E> {
	}

	/**
	 * A transformer capable of transforming an {@link ObservableCollection} into another {@link ObservableCollection}
	 *
	 * @param <MV1> The model instance type of the target collection
	 * @param <S> The type of the source collection
	 * @param <T> The type of the target collection
	 * @param <MV2> The type of the target observable structure
	 */
	public interface FlowTransformInstantiator<MV1 extends ObservableCollection<?>, MV2 extends ObservableCollection<?>, S, T>
	extends Operation.Instantiator<MV1, MV2>, FlowTransform2<MV1, MV2, S, T> {
		/**
		 * Transforms a collection flow
		 *
		 * @param source The source flow
		 * @param models The models to do the transformation
		 * @return The transformed flow
		 * @throws ModelInstantiationException If the transformation fails
		 */
		@Override
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
		@Override
		CollectionDataFlow<?, ?, T> transformToFlow(MV1 source, ModelSetInstance models) throws ModelInstantiationException;
	}

	public interface FlowTransform2<M1 extends ObservableCollection<?>, M2 extends ObservableCollection<?>, S, T>
	extends TransformInstantiator<M1, M2> {
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
		CollectionDataFlow<?, ?, T> transformToFlow(M1 source, ModelSetInstance models) throws ModelInstantiationException;

		@Override
		default M2 transform(M1 source, ModelSetInstance models) throws ModelInstantiationException {
			ObservableCollection.CollectionDataFlow<?, ?, T> flow = transformToFlow(source, models);
			return (M2) flow.collect();
		}

		@Override
		default <S0> TransformInstantiator<S0, M2> after(TransformInstantiator<S0, ? extends M1> before) {
			if (before instanceof FlowTransform2) {
				FlowTransform2<ObservableCollection<?>, ? extends M1, Object, S> flowBefore = (FlowTransform2<ObservableCollection<?>, ? extends M1, Object, S>) before;
				FlowTransform2<M1, M2, S, T> next = this;
				return (TransformInstantiator<S0, M2>) new FlowTransform2<ObservableCollection<?>, M2, Object, T>() {
					@Override
					public CollectionDataFlow<?, ?, T> transformFlow(CollectionDataFlow<?, ?, Object> source, ModelSetInstance models)
						throws ModelInstantiationException {
						CollectionDataFlow<?, ?, S> sourceFlow = flowBefore.transformFlow(source, models);
						return next.transformFlow(sourceFlow, models);
					}

					@Override
					public CollectionDataFlow<?, ?, T> transformToFlow(ObservableCollection<?> source, ModelSetInstance models)
						throws ModelInstantiationException {
						CollectionDataFlow<?, ?, S> sourceFlow = flowBefore.transformToFlow(source, models);
						return next.transformFlow(sourceFlow, models);
					}

					@Override
					public String toString() {
						return flowBefore + "->" + next;
					}
				};
			} else
				return TransformInstantiator.super.after(before);
		}
	}

	public static void configureTransformation(QonfigInterpreterCore.Builder interpreter) {
		interpreter.createWith("transform", ModelValueElement.CompiledSynth.class, ExElement.creator(ExpressoTransformedElement::new));

		ObservableTransformations.configureTransformation(interpreter);

		ObservableActionTransformations.configureTransformation(interpreter);

		ObservableValueTransformations.configureTransformation(interpreter);

		ObservableCollectionTransformations.configureTransformation(interpreter);

		interpreter.createWith("map-with", MapWith.class, session -> {
			ExElement.Def<?> parent = session.as(ExpressoQIS.class).getElementRepresentation();
			if (!(parent instanceof AbstractCompiledTransformation))
				throw new QonfigInterpretationException(
					"This interpretation may only be used by a parent whose interpretation is an instance of "
						+ AbstractCompiledTransformation.class.getName(),
						session.getElement().getPositionInFile(), session.getElement().getType().getName().length());
			return new MapWith<>((AbstractCompiledTransformation<?, ?, ?>) parent, session.getFocusType());
		});
		interpreter.createWith("combine-with", CombineWith.class, session -> {
			ExElement.Def<?> parent = session.as(ExpressoQIS.class).getElementRepresentation();
			if (!(parent instanceof AbstractCompiledTransformation))
				throw new QonfigInterpretationException(
					"This interpretation may only be used by a parent whose interpretation is an instance of "
						+ AbstractCompiledTransformation.class.getName(),
						session.getElement().getPositionInFile(), session.getElement().getType().getName().length());
			return new CombineWith<>((AbstractCompiledTransformation<?, ?, ?>) parent, session.getFocusType());
		});
		interpreter.delegateToType("map-reverse", "type", CompiledMapReverse.class);
		interpreter.createWith("replace-source", CompiledMapReverse.class, session -> {
			ExElement.Def<?> parent = session.as(ExpressoQIS.class).getElementRepresentation();
			if (!(parent instanceof CompiledTransformation))
				throw new QonfigInterpretationException(
					"This interpretation may only be used by a parent whose interpretation is an instance of "
						+ CompiledTransformation.class.getName(),
						session.getElement().getPositionInFile(), session.getElement().getType().getName().length());
			return new SourceReplaceReverse<>((CompiledTransformation<?, ?, ?>) parent, session.getFocusType());
		});
		interpreter.createWith("modify-source", CompiledMapReverse.class, session -> {
			ExElement.Def<?> parent = session.as(ExpressoQIS.class).getElementRepresentation();
			if (!(parent instanceof CompiledTransformation))
				throw new QonfigInterpretationException(
					"This interpretation may only be used by a parent whose interpretation is an instance of "
						+ CompiledTransformation.class.getName(),
						session.getElement().getPositionInFile(), session.getElement().getType().getName().length());
			return new SourceModifyingReverse<>((CompiledTransformation<?, ?, ?>) parent, session.getFocusType());
		});
	}

	public static abstract class TypePreservingTransform<M> extends ExElement.Def.Abstract<ExElement>
	implements Operation<M, M, ExElement> {
		private ModelType<M> theModelType;

		protected TypePreservingTransform(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType);
		}

		@Override
		public ModelType<? extends M> getTargetModelType() {
			return theModelType;
		}

		@Override
		public void update(ExpressoQIS session, ModelType<M> sourceModelType) throws QonfigInterpretationException {
			theModelType = sourceModelType;
			super.update(session);
		}

		@Override
		public Operation.Interpreted<M, ?, M, ?, ? extends ExElement> interpret(ExElement.Interpreted<?> parent)
			throws ExpressoInterpretationException {
			return tppInterpret(parent);
		}

		protected abstract Interpreted<M, ?> tppInterpret(ExElement.Interpreted<?> parent);

		public static abstract class Interpreted<M, MV extends M> extends ExElement.Interpreted.Abstract<ExElement>
		implements Operation.Interpreted<M, MV, M, MV, ExElement> {
			private ModelInstanceType<M, MV> theType;

			protected Interpreted(Def<? super ExElement> definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public void update(ModelInstanceType<M, MV> sourceType, InterpretedExpressoEnv env) throws ExpressoInterpretationException {
				super.update(env);
				theType = sourceType;
			}

			@Override
			public ModelInstanceType<? extends M, ? extends MV> getTargetType() {
				return theType;
			}
		}
	}

	public static InterpretedValueSynth<SettableValue<?>, SettableValue<String>> parseFilter(CompiledExpression testX,
		ExElement.Interpreted<?> element, boolean preferMessage) throws ExpressoInterpretationException {
		if (testX == null)
			return null;
		InterpretedValueSynth<SettableValue<?>, SettableValue<String>> test;
		try {
			if (preferMessage)
				test = element.interpret(testX, ModelTypes.Value.forType(String.class));
			else {
				test = element.interpret(testX, ModelTypes.Value.forType(boolean.class))//
					.mapValue(ModelTypes.Value.forType(String.class),
						bv -> SettableValue.asSettable(bv.map(String.class, b -> b ? null : "Not allowed"), __ -> "Not settable"));
			}
		} catch (ExpressoInterpretationException e) {
			try {
				if (preferMessage) {
					test = element.interpret(testX, ModelTypes.Value.forType(boolean.class))//
						.mapValue(ModelTypes.Value.forType(String.class),
							bv -> SettableValue.asSettable(bv.map(String.class, b -> b ? null : "Not allowed"), __ -> "Not settable"));
				} else
					test = element.interpret(testX, ModelTypes.Value.forType(String.class));
			} catch (ExpressoInterpretationException e2) {
				throw new ExpressoInterpretationException("Could not interpret '" + testX + "' as a String or a boolean", e.getPosition(),
					e.getErrorLength(), e);
			}
		}
		return test;
	}

	/** A compiled structure capable of generating an {@link InterpretedTransformation} */
	public interface CompiledTransformation<M1, M2, E extends ExElement> extends Operation<M1, M2, E> {
		ModelComponentId getSourceName();

		@Override
		Interpreted<M1, ?, ?, ?, M2, ?, ? extends E> interpret(ExElement.Interpreted<?> parent) throws ExpressoInterpretationException;

		/** @return Whether this transformation is reversible */
		boolean isReversible();

		public interface Interpreted<M1, S, T, MV1 extends M1, M2, MV2 extends M2, E extends ExElement>
		extends Operation.Interpreted<M1, MV1, M2, MV2, E> {
			@Override
			CompiledTransformation<M1, M2, ? super E> getDefinition();

			/** @return The type of value that this transformation produces */
			TypeToken<T> getTargetValueType();

			@Override
			Instantiator<S, T, MV1, MV2> instantiate() throws ModelInstantiationException;
		}

		public interface Instantiator<S, T, MV1, MV2> extends Operation.Instantiator<MV1, MV2> {
			/**
			 * @param models The model instances to use
			 * @return The observable transformation
			 * @throws ModelInstantiationException If the transformation could not be produced
			 */
			default Transformation<S, T> transform(ModelSetInstance models) throws ModelInstantiationException {
				Transformation.ReversibleTransformationPrecursor<S, T, ?> precursor;
				precursor = new Transformation.ReversibleTransformationPrecursor<>();
				return transform(precursor, models);
			}

			/**
			 * @param models The model instances to use
			 * @return The observable transformation
			 * @throws ModelInstantiationException If the transformation could not be produced
			 */
			Transformation<S, T> transform(Transformation.ReversibleTransformationPrecursor<S, T, ?> precursor, ModelSetInstance models)
				throws ModelInstantiationException;
		}
	}

	/** A compiled structure capable of generating an {@link InterpretedMapReverse} */
	public interface CompiledMapReverse<E extends ExElement> extends ExElement.Def<E> {
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
		<S, T> Interpreted<S, T, ? extends E> interpret(ExElement.Interpreted<?> parent) throws ExpressoInterpretationException;

		public interface Interpreted<S, T, E extends ExElement> extends ExElement.Interpreted<E> {
			/**
			 * @param <S> The type of the source values to produce
			 * @param <T> The type of the mapped values to reverse-transform
			 * @param sourceName The source model value
			 * @param sourceType The source type of the transformation
			 * @param targetType The target type of the transformation
			 * @param combinedTypes The names and types of all combined values incorporated into the transform
			 * @throws ExpressoInterpretationException If the reverse transformation could not be produced
			 */
			void update(ModelComponentId sourceName, TypeToken<S> sourceType, TypeToken<T> targetType, InterpretedExpressoEnv env)
				throws ExpressoInterpretationException;

			/** @return Any component model values that are used by this reverse */
			List<? extends InterpretedValueSynth<?, ?>> getComponents();

			Instantiator<S, T> instantiate() throws ModelInstantiationException;
		}

		public interface Instantiator<S, T> {
			void instantiate() throws ModelInstantiationException;

			/**
			 * @param transformation The transformation to reverse
			 * @param models The model instance
			 * @return The transformation reverse
			 * @throws ModelInstantiationException If the reverse operation could not be instantiated
			 */
			Transformation.TransformReverse<S, T> reverse(SettableValue<S> sourceValue, TypeToken<T> targetType,
				List<CombineWith.TransformationModification<S, T>> modifications, Transformation<S, T> transformation,
				ModelSetInstance models) throws ModelInstantiationException;

			/**
			 * Helps support the {@link ModelValueSynth#forModelCopy(Object, ModelSetInstance, ModelSetInstance)} method
			 *
			 * @param sourceModels The source model instance
			 * @param newModels The new model instance
			 * @return Whether observables produced by this reverse would be different between the two models
			 * @throws ModelInstantiationException If the inspection fails
			 */
			boolean isDifferent(ModelSetInstance sourceModels, ModelSetInstance newModels) throws ModelInstantiationException;
		}
	}

	@ExMultiElementTraceable({
		@ExElementTraceable(toolkit = ExpressoBaseV0_1.BASE,
			qonfigType = "map-to",
			interpretation = AbstractCompiledTransformation.Interpreted.class),
		@ExElementTraceable(toolkit = ExpressoBaseV0_1.BASE,
		qonfigType = "abst-map-op",
		interpretation = AbstractCompiledTransformation.Interpreted.class) })
	public static abstract class AbstractCompiledTransformation<M1, M2, E extends ExElement> extends ExElement.Def.Abstract<E>
	implements CompiledTransformation<M1, M2, E> {
		private ModelComponentId theSourceName;
		private VariableType theType;
		private MapWith<?> theMapWith;
		private CompiledExpression theEquivalence;
		private final List<CombineWith<?>> theCombinedValues;
		private CompiledMapReverse<?> theReverse;

		private boolean isCached;
		private boolean isReEvalOnUpdate;
		private boolean isFireIfUnchanged;
		private boolean isNullToNull;
		private boolean isManyToOne;
		private boolean isOneToMany;

		protected AbstractCompiledTransformation(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType);
			theCombinedValues = new ArrayList<>();
		}

		@Override
		public ModelComponentId getSourceName() {
			return theSourceName;
		}

		public VariableType getType() {
			return theType;
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

		@QonfigChildGetter(asType = "map-to", value = "map")
		public MapWith<?> getMapWith() {
			return theMapWith;
		}

		@QonfigAttributeGetter(asType = "map-to", value = "equivalence")
		public CompiledExpression getEquivalence() {
			return theEquivalence;
		}

		@QonfigChildGetter(asType = "map-to", value = "combined-value")
		public List<CombineWith<?>> getCombinedValues() {
			return Collections.unmodifiableList(theCombinedValues);
		}

		@QonfigChildGetter(asType = "map-to", value = "reverse")
		public CompiledMapReverse<?> getReverse() {
			return theReverse;
		}

		@Override
		public void update(ExpressoQIS session, ModelType<M1> sourceModelType) throws QonfigInterpretationException {
			super.update(session);
			// This is quicker than retrieving and grabbing it from the add-on
			String sourceAs = session.getAttributeText("source-as");
			ExWithElementModel.Def withElModel = getAddOn(ExWithElementModel.Def.class);
			theSourceName = withElModel.getElementValueModelId(sourceAs);
			theType = getAddOnValue(ExTyped.Def.class, ExTyped.Def::getValueType);
			isCached = session.getAttribute("cache", boolean.class);
			isReEvalOnUpdate = session.getAttribute("re-eval-on-update", boolean.class);
			isFireIfUnchanged = session.getAttribute("fire-if-unchanged", boolean.class);
			isNullToNull = session.getAttribute("null-to-null", boolean.class);
			isManyToOne = session.getAttribute("many-to-one", boolean.class);
			isOneToMany = session.getAttribute("one-to-many", boolean.class);

			theMapWith = syncChild(MapWith.class, theMapWith, session, "map");
			theEquivalence = getAttributeExpression("equivalence", session);
			syncChildren(CombineWith.class, theCombinedValues, session.forChildren("combined-value"));
			theReverse = syncChild(CompiledMapReverse.class, theReverse, session, "reverse");

			for (CombineWith<?> combine : theCombinedValues)
				withElModel.satisfyElementValueType(combine.getValueVariable(), ModelTypes.Value, //
					(interp, env) -> interp.interpret(combine.getElementValue(), ModelTypes.Value.any()).getType());
			withElModel.<Interpreted<M1, ?, ?, ?, M2, ?, E>, SettableValue<?>> satisfyElementValueType(theSourceName, ModelTypes.Value, //
				(interp, env) -> ModelTypes.Value.forType(interp.getSourceType()));
		}

		@Override
		public boolean isReversible() {
			return theReverse != null;
		}

		protected static abstract class Interpreted<M1, S, T, MV1 extends M1, M2, MV2 extends M2, E extends ExElement>
		extends ExElement.Interpreted.Abstract<E> implements CompiledTransformation.Interpreted<M1, S, T, MV1, M2, MV2, E> {
			private TypeToken<S> theSourceType;
			private TypeToken<T> theEvaluatedTargetType;
			private MapWith.Interpreted<S, T, ?> theMapWith;
			private InterpretedValueSynth<SettableValue<?>, SettableValue<Equivalence<? super T>>> theEquivalence;
			private final List<CombineWith.Interpreted<?, ?>> theCombinedValues;
			private CompiledMapReverse.Interpreted<S, T, ?> theReverse;

			protected Interpreted(AbstractCompiledTransformation<M1, M2, ? super E> definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
				theCombinedValues = new ArrayList<>();
			}

			@Override
			public AbstractCompiledTransformation<M1, M2, ? super E> getDefinition() {
				return (AbstractCompiledTransformation<M1, M2, ? super E>) super.getDefinition();
			}

			public TypeToken<S> getSourceType() {
				return theSourceType;
			}

			public TypeToken<T> getEvaluatedTargetType() {
				return theEvaluatedTargetType;
			}

			public MapWith.Interpreted<S, T, ?> getMapWith() {
				return theMapWith;
			}

			public InterpretedValueSynth<SettableValue<?>, SettableValue<Equivalence<? super T>>> getEquivalence() {
				return theEquivalence;
			}

			public List<CombineWith.Interpreted<?, ?>> getCombinedValues() {
				return Collections.unmodifiableList(theCombinedValues);
			}

			public CompiledMapReverse.Interpreted<S, T, ?> getReverse() {
				return theReverse;
			}

			@Override
			public void update(ModelInstanceType<M1, MV1> sourceType, InterpretedExpressoEnv env) throws ExpressoInterpretationException {
				theSourceType = (TypeToken<S>) sourceType.getType(0);
				theEvaluatedTargetType = getDefinition().getType() == null ? null
					: (TypeToken<T>) getDefinition().getType().getType(getExpressoEnv());
				super.update(env);
				// ??? We need to do the combined values first, because the map-with may (and most likely does) use the combined value
				// variables
				syncChildren(getDefinition().getCombinedValues(), theCombinedValues, def -> def.interpret(this),
					(i, vEnv) -> i.update(vEnv));
				theMapWith = syncChild(getDefinition().getMapWith(), theMapWith, def -> def.interpret(this),
					(m, mEnv) -> m.update(theSourceType, mEnv));
				if (getDefinition().getEquivalence() == null)
					theEquivalence = null;
				else {
					theEquivalence = interpret(getDefinition().getEquivalence(),
						ModelTypes.Value.forType(TypeTokens.get().keyFor(Equivalence.class)
							.<Equivalence<? super T>> parameterized(TypeTokens.get().getSuperWildcard(theMapWith.getTargetType()))));
				}
				theReverse = syncChild(getDefinition().getReverse(), theReverse, def -> def.interpret(this),
					(r, rEnv) -> r.update(getDefinition().getSourceName(), theMapWith.getSourceType(), theMapWith.getTargetType(), rEnv));
			}

			@Override
			public TypeToken<T> getTargetValueType() {
				return theMapWith.getTargetType();
			}

			@Override
			public BetterList<InterpretedValueSynth<?, ?>> getComponents() {
				List<InterpretedValueSynth<?, ?>> components = new ArrayList<>();
				components.addAll(theMapWith.getComponents());
				if (theEquivalence != null)
					components.add(theEquivalence);
				if (theReverse != null)
					components.addAll(theReverse.getComponents());
				return BetterList.of(components);
			}

			@Override
			public String toString() {
				return "map(" + theMapWith.getMap() + ")";
			}
		}

		public static abstract class Instantiator<S, T, MV1, MV2> implements CompiledTransformation.Instantiator<S, T, MV1, MV2> {
			private final ModelInstantiator theLocalModel;
			private final MapWith.Instantiator<S, T> theMapWith;
			private final List<CombineWith.Instantiator<?>> theCombinedValues;
			private final CompiledMapReverse.Instantiator<S, T> theReverse;
			private final ModelComponentId theSourceVariable;
			private final boolean isCached;
			private final boolean isReEvalOnUpdate;
			private final boolean isFireIfUnchanged;
			private final boolean isNullToNull;
			private final boolean isManyToOne;
			private final boolean isOneToMany;
			private final ModelValueInstantiator<SettableValue<Equivalence<? super T>>> theEquivalence;

			protected Instantiator(ModelInstantiator localModel, MapWith.Instantiator<S, T> mapWith,
				List<CombineWith.Instantiator<?>> combinedValues, CompiledMapReverse.Instantiator<S, T> reverse,
				ModelComponentId sourceVariable, boolean cached, boolean reEvalOnUpdate, boolean fireIfUnchanged, boolean nullToNull,
				boolean manyToOne, boolean oneToMany, ModelValueInstantiator<SettableValue<Equivalence<? super T>>> equivalence) {
				theLocalModel = localModel;
				theMapWith = mapWith;
				theCombinedValues = combinedValues;
				theReverse = reverse;
				theSourceVariable = sourceVariable;
				isCached = cached;
				isReEvalOnUpdate = reEvalOnUpdate;
				isFireIfUnchanged = fireIfUnchanged;
				isNullToNull = nullToNull;
				isManyToOne = manyToOne;
				isOneToMany = oneToMany;
				theEquivalence = equivalence;
			}

			@Override
			public void instantiate() throws ModelInstantiationException {
				theLocalModel.instantiate();
				theMapWith.instantiate();
				for (CombineWith.Instantiator<?> combinedValue : theCombinedValues)
					combinedValue.instantiate();
				if (theReverse != null)
					theReverse.instantiate();
				if (theEquivalence != null)
					theEquivalence.instantiate();
			}

			@Override
			public Transformation<S, T> transform(Transformation.ReversibleTransformationPrecursor<S, T, ?> precursor,
				ModelSetInstance models) throws ModelInstantiationException {
				models = theLocalModel.wrap(models);
				SettableValue<S> sourceV = SettableValue.build(theMapWith.getSourceType())//
					.withValue(TypeTokens.get().getDefaultValue(theMapWith.getSourceType()))//
					.build();
				ExFlexibleElementModelAddOn.satisfyElementValue(theSourceVariable, models, sourceV);
				Transformation.ReversibleTransformationBuilder<S, T, ?> builder = precursor//
					.cache(isCached)//
					.reEvalOnUpdate(isReEvalOnUpdate)//
					.fireIfUnchanged(isFireIfUnchanged)//
					.nullToNull(isNullToNull)//
					.manyToOne(isManyToOne)//
					.oneToMany(isOneToMany);
				List<CombineWith.TransformationModification<S, T>> modifications = new ArrayList<>(theCombinedValues.size());
				for (CombineWith.Instantiator<?> combinedValue : theCombinedValues)
					builder = combinedValue.addTo(builder, models, modifications::add);
				modifications = Collections.unmodifiableList(modifications);

				SettableValue<T> targetV = theMapWith.getMap().get(models);
				MaybeReversibleTransformation<S, T> transformation = theMapWith.buildTransformation(builder, sourceV, targetV,
					modifications, models);

				Transformation<S, T> finalTransformation;
				if (theReverse != null)
					finalTransformation = transformation.withReverse(//
						theReverse.reverse(sourceV, targetV.getType(), modifications, transformation, models));
				else
					finalTransformation = transformation.withReverse(//
						theMapWith.reverse(sourceV, targetV, modifications, transformation, models));

				if (theEquivalence != null) {
					Equivalence<? super T> equivalence = theEquivalence.get(models).get();
					finalTransformation = finalTransformation.withEquivalence(equivalence);
				}
				return finalTransformation;
			}

			@Override
			public boolean isDifferent(ModelSetInstance sourceModels, ModelSetInstance newModels) throws ModelInstantiationException {
				if (theLocalModel != null) {
					sourceModels = theLocalModel.wrap(sourceModels);
					newModels = theLocalModel.wrap(newModels);
				}
				if (theMapWith.isDifferent(sourceModels, newModels))
					return true;
				else if (theEquivalence != null) {
					SettableValue<Equivalence<? super T>> eq = theEquivalence.get(sourceModels);
					if (eq != theEquivalence.forModelCopy(eq, sourceModels, newModels))
						return true;
				}
				for (CombineWith.Instantiator<?> combinedValue : theCombinedValues)
					if (combinedValue.isDifferent(sourceModels, newModels))
						return true;
				if (theReverse != null && theReverse.isDifferent(sourceModels, newModels))
					return true;
				return false;
			}
		}

		public static abstract class EfficientCopyingInstantiator<S, T, MV1, MV2> extends Instantiator<S, T, MV1, MV2>
		implements Operation.EfficientCopyingInstantiator<MV1, MV2> {
			protected EfficientCopyingInstantiator(ModelInstantiator localModel, MapWith.Instantiator<S, T> mapWith,
				List<CombineWith.Instantiator<?>> combinedValues, CompiledMapReverse.Instantiator<S, T> reverse,
				ModelComponentId sourceVariable, boolean cached, boolean reEvalOnUpdate, boolean fireIfUnchanged, boolean nullToNull,
				boolean manyToOne, boolean oneToMany, ModelValueInstantiator<SettableValue<Equivalence<? super T>>> equivalence) {
				super(localModel, mapWith, combinedValues, reverse, sourceVariable, cached, reEvalOnUpdate, fireIfUnchanged, nullToNull,
					manyToOne, oneToMany, equivalence);
			}

			@Override
			public MV2 forModelCopy(MV2 prevValue, MV1 newSource, ModelSetInstance sourceModels, ModelSetInstance newModels)
				throws ModelInstantiationException {
				if (newSource == getSource(prevValue) && !isDifferent(sourceModels, newModels))
					return prevValue;
				else
					return transform(newSource, newModels);
			}
		}
	}

	@ExElementTraceable(toolkit = ExpressoBaseV0_1.BASE, qonfigType = "map-with", interpretation = MapWith.Interpreted.class)
	public static class MapWith<E extends ExElement> extends ExElement.Def.Abstract<E> {
		private CompiledExpression theMap;

		public MapWith(AbstractCompiledTransformation<?, ?, ?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType);
		}

		@Override
		public AbstractCompiledTransformation<?, ?, ?> getParentElement() {
			return (AbstractCompiledTransformation<?, ?, ?>) super.getParentElement();
		}

		@QonfigAttributeGetter
		public CompiledExpression getMap() {
			return theMap;
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			super.doUpdate(session);
			theMap = getValueExpression(session);
		}

		public <S, T> Interpreted<S, T, ? extends E> interpret(AbstractCompiledTransformation.Interpreted<?, S, T, ?, ?, ?, ?> parent) {
			return new Interpreted<>(this, parent);
		}

		protected static class Interpreted<S, T, E extends ExElement> extends ExElement.Interpreted.Abstract<E> {
			private TypeToken<S> theSourceType;
			private InterpretedValueSynth<SettableValue<?>, SettableValue<T>> theMap;
			private boolean isTesting;

			protected Interpreted(MapWith<? super E> definition, AbstractCompiledTransformation.Interpreted<?, S, T, ?, ?, ?, ?> parent) {
				super(definition, parent);
			}

			@Override
			public MapWith<? super E> getDefinition() {
				return (MapWith<? super E>) super.getDefinition();
			}

			@Override
			public AbstractCompiledTransformation.Interpreted<?, S, T, ?, ?, ?, ?> getParentElement() {
				return (AbstractCompiledTransformation.Interpreted<?, S, T, ?, ?, ?, ?>) super.getParentElement();
			}

			public InterpretedValueSynth<SettableValue<?>, SettableValue<T>> getMap() {
				return theMap;
			}

			public TypeToken<S> getSourceType() {
				return theSourceType;
			}

			public TypeToken<T> getTargetType() {
				return (TypeToken<T>) theMap.getType().getType(0);
			}

			public void update(TypeToken<S> sourceType, InterpretedExpressoEnv env) throws ExpressoInterpretationException {
				theSourceType = sourceType;
				super.update(env);
				if (getParentElement().getEvaluatedTargetType() != null)
					theMap = interpret(getDefinition().getMap(), ModelTypes.Value.forType(getParentElement().getEvaluatedTargetType()));
				else
					theMap = interpret(getDefinition().getMap(), ModelTypes.Value.<SettableValue<T>> anyAs());
				isTesting = env.isTesting();
			}

			public BetterList<InterpretedValueSynth<?, ?>> getComponents() {
				return BetterList.of(theMap);
			}

			public Instantiator<S, T> instantiate() throws ModelInstantiationException {
				return new Instantiator<>(theSourceType, theMap.instantiate(), isTesting);
			}
		}

		public static class Instantiator<S, T> {
			private final TypeToken<S> theSourceType;
			private final ModelValueInstantiator<SettableValue<T>> theMap;
			private final boolean isTesting;

			public Instantiator(TypeToken<S> sourceType, ModelValueInstantiator<SettableValue<T>> map, boolean testing) {
				theSourceType = sourceType;
				theMap = map;
				isTesting = testing;
			}

			public TypeToken<S> getSourceType() {
				return theSourceType;
			}

			public ModelValueInstantiator<SettableValue<T>> getMap() {
				return theMap;
			}

			public void instantiate() throws ModelInstantiationException {
				theMap.instantiate();
			}

			public MaybeReversibleTransformation<S, T> buildTransformation(ReversibleTransformationBuilder<S, T, ?> builder,
				SettableValue<S> sourceValue, SettableValue<T> mappedValue,
				List<CombineWith.TransformationModification<S, T>> modifications, ModelSetInstance models)
					throws ModelInstantiationException {
				BiFunction<S, Transformation.TransformationValues<? extends S, ? extends T>, T> mapFn = LambdaUtils
					.printableBiFn((source, tvs) -> {
						sourceValue.set(source, null);
						for (CombineWith.TransformationModification<S, T> mod : modifications)
							mod.prepareTransformOperation(tvs);
						return mappedValue.get();
					}, theMap::toString, null);
				return builder.build(mapFn).withTesting(isTesting);
			}

			public TransformReverse<S, T> reverse(SettableValue<S> sourceV, SettableValue<T> mappedValue,
				List<TransformationModification<S, T>> modifications, MaybeReversibleTransformation<S, T> transformation,
				ModelSetInstance models) throws ModelInstantiationException {
				BiFunction<T, Transformation.TransformationValues<? extends S, ? extends T>, S> reverseFn;
				Function<Transformation.TransformationValues<? extends S, ? extends T>, String> enabledFn;
				BiFunction<T, Transformation.TransformationValues<? extends S, ? extends T>, String> acceptFn;
				TriFunction<T, Transformation.TransformationValues<? extends S, ? extends T>, Boolean, S> addFn;
				BiFunction<T, Transformation.TransformationValues<? extends S, ? extends T>, String> addAcceptFn;
				reverseFn = (target, tvs) -> {
					prepareTx(tvs, modifications);
					mappedValue.set(target, null);
					return sourceV.get();
				};
				enabledFn = tvs -> {
					prepareTx(tvs, modifications);
					return mappedValue.isEnabled().get();
				};
				acceptFn = (target, tvs) -> {
					prepareTx(tvs, modifications);
					return mappedValue.isAcceptable(target);
				};
				addFn = (target, tvs, test) -> {
					return reverseFn.apply(target, tvs);
				};
				addAcceptFn = acceptFn;
				return new Transformation.SourceReplacingReverse<>(transformation, reverseFn, enabledFn, acceptFn, addFn, addAcceptFn,
					false, false);
			}

			/**
			 * A utility function to populate needed model values for each transformation operation. Must be static because this is invoked
			 * from the evaluated phase.
			 */
			protected static <S, T> void prepareTx(Transformation.TransformationValues<? extends S, ? extends T> tvs,
				List<CombineWith.TransformationModification<S, T>> modifications) {
				for (CombineWith.TransformationModification<S, T> mod : modifications)
					mod.prepareTransformOperation(tvs);
			}

			public boolean isDifferent(ModelSetInstance sourceModels, ModelSetInstance newModels) throws ModelInstantiationException {
				SettableValue<T> map = theMap.get(sourceModels);
				return map != theMap.forModelCopy(map, sourceModels, newModels);
			}
		}
	}

	@ExElementTraceable(toolkit = ExpressoBaseV0_1.BASE, qonfigType = "combine-with", interpretation = CombineWith.Interpreted.class)
	public static class CombineWith<E extends ExElement> extends ExElement.Def.Abstract<E> implements Named {
		private CompiledExpression theValue;
		private ModelComponentId theValueVariable;

		public CombineWith(AbstractCompiledTransformation<?, ?, ?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType);
		}

		@Override
		public AbstractCompiledTransformation<?, ?, ?> getParentElement() {
			return (AbstractCompiledTransformation<?, ?, ?>) super.getParentElement();
		}

		@Override
		public String getName() {
			return getAddOn(ExNamed.Def.class).getName();
		}

		public ModelComponentId getValueVariable() {
			return theValueVariable;
		}

		@QonfigAttributeGetter
		@Override
		public CompiledExpression getElementValue() {
			return theValue;
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			super.doUpdate(session);
			theValue = getValueExpression(session);
			theValueVariable = getParentElement().getAddOn(ExWithElementModel.Def.class).getElementValueModelId(getName());
		}

		public Interpreted<?, ? extends E> interpret(AbstractCompiledTransformation.Interpreted<?, ?, ?, ?, ?, ?, ?> parent)
			throws ExpressoInterpretationException {
			return new Interpreted<>(this, parent);
		}

		protected static class Interpreted<T, E extends ExElement> extends ExElement.Interpreted.Abstract<E> {
			private InterpretedValueSynth<SettableValue<?>, SettableValue<T>> theValue;

			public Interpreted(CombineWith<? super E> definition, AbstractCompiledTransformation.Interpreted<?, ?, ?, ?, ?, ?, ?> parent) {
				super(definition, parent);
			}

			@Override
			public CombineWith<? super E> getDefinition() {
				return (CombineWith<? super E>) super.getDefinition();
			}

			void getValue(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
				theValue = interpret(getDefinition().getElementValue(), ModelTypes.Value.<SettableValue<T>> anyAs());
			}

			@Override
			public AbstractCompiledTransformation.Interpreted<?, ?, ?, ?, ?, ?, ?> getParentElement() {
				return (AbstractCompiledTransformation.Interpreted<?, ?, ?, ?, ?, ?, ?>) super.getParentElement();
			}

			public InterpretedValueSynth<SettableValue<?>, SettableValue<T>> getElementValue() {
				return theValue;
			}

			@Override
			protected void doUpdate(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
				super.doUpdate(env);
				getValue(env);
			}

			public Instantiator<T> instantiate() throws ModelInstantiationException {
				return new Instantiator<>(theValue.instantiate(), getDefinition().getValueVariable());
			}
		}

		public static class Instantiator<T> {
			private final ModelValueInstantiator<SettableValue<T>> theValue;
			private final ModelComponentId theValueVariable;

			public Instantiator(ModelValueInstantiator<SettableValue<T>> value, ModelComponentId valueVariable) {
				theValue = value;
				theValueVariable = valueVariable;
			}

			void instantiate() throws ModelInstantiationException {
				theValue.instantiate();
			}

			public <S, T2> ReversibleTransformationBuilder<S, T2, ?> addTo(ReversibleTransformationBuilder<S, T2, ?> builder,
				ModelSetInstance models, Consumer<? super TransformationModification<S, T2>> modify) throws ModelInstantiationException {
				SettableValue<T> sourceV = theValue.get(models);
				SettableValue<T> targetV = SettableValue.build(sourceV.getType())
					.withValue(TypeTokens.get().getDefaultValue(sourceV.getType())).build();
				ExFlexibleElementModelAddOn.satisfyElementValue(theValueVariable, models,
					targetV.disableWith(SettableValue.ALWAYS_DISABLED));
				modify.accept(new DefaultTransformationModification<>(sourceV, targetV));
				return builder.combineWith(sourceV);
			}

			public boolean isDifferent(ModelSetInstance sourceModels, ModelSetInstance newModels) throws ModelInstantiationException {
				SettableValue<T> sourceV = theValue.get(sourceModels);
				return sourceV != theValue.forModelCopy(sourceV, sourceModels, newModels);
			}
		}

		public interface TransformationModification<S, T> {
			void prepareTransformOperation(Transformation.TransformationValues<? extends S, ? extends T> txValues);
		}

		public static class DefaultTransformationModification<T, S, T2> implements TransformationModification<S, T2> {
			private final SettableValue<T> theSourceValue;
			private final SettableValue<T> theTargetValue;

			public DefaultTransformationModification(SettableValue<T> sourceValue, SettableValue<T> targetValue) {
				theSourceValue = sourceValue;
				theTargetValue = targetValue;
			}

			@Override
			public void prepareTransformOperation(Transformation.TransformationValues<? extends S, ? extends T2> txValues) {
				T cv = txValues.get(theSourceValue);
				theTargetValue.set(cv, null);
			}
		}
	}

	@ExMultiElementTraceable({
		@ExElementTraceable(toolkit = ExpressoBaseV0_1.BASE,
			qonfigType = "map-reverse",
			interpretation = AbstractMapReverse.Interpreted.class),
		@ExElementTraceable(toolkit = ExpressoBaseV0_1.BASE,
		qonfigType = "modify-source",
		interpretation = AbstractMapReverse.Interpreted.class) })
	public static abstract class AbstractMapReverse<E extends ExElement> extends ExElement.Def.Abstract<E>
	implements CompiledMapReverse<E> {
		private QonfigAddOn theType;
		private ModelComponentId theTargetVariable;
		private CompiledExpression theReverse;
		private CompiledExpression theEnabled;
		private CompiledExpression theAccept;
		private CompiledExpression theAdd;
		private CompiledExpression theAddAccept;
		private boolean isStateful;

		protected AbstractMapReverse(CompiledTransformation<?, ?, ?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType);
		}

		@Override
		public CompiledTransformation<?, ?, ?> getParentElement() {
			return (CompiledTransformation<?, ?, ?>) super.getParentElement();
		}

		@QonfigAttributeGetter(asType = "map-reverse", value = "type")
		public QonfigAddOn getType() {
			return theType;
		}

		@QonfigAttributeGetter(asType = "map-reverse", value = "target-as")
		public ModelComponentId getTargetVariable() {
			return theTargetVariable;
		}

		@QonfigAttributeGetter(asType = "map-reverse")
		public CompiledExpression getReverse() {
			return theReverse;
		}

		@QonfigAttributeGetter(asType = "modify-source", value = "enabled")
		public CompiledExpression getEnabled() {
			return theEnabled;
		}

		@QonfigAttributeGetter(asType = "modify-source", value = "accept")
		public CompiledExpression getAccept() {
			return theAccept;
		}

		@QonfigAttributeGetter(asType = "modify-source", value = "add")
		public CompiledExpression getAdd() {
			return theAdd;
		}

		@QonfigAttributeGetter(asType = "modify-source", value = "add-accept")
		public CompiledExpression getAddAccept() {
			return theAddAccept;
		}

		public boolean isStateful() {
			return isStateful;
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			super.doUpdate(session);
			theType = session.getAttribute("type", QonfigAddOn.class);
			String targetName = session.getAttributeText("target-as");
			theTargetVariable = getAddOn(ExWithElementModel.Def.class).getElementValueModelId(targetName);
			theReverse = getValueExpression(session);
			theEnabled = getAttributeExpression("enabled", session);
			theAccept = getAttributeExpression("accept", session);
			theAdd = getAttributeExpression("add", session);
			theAddAccept = getAttributeExpression("add-accept", session);

			if (theAdd == null && theAddAccept != null)
				reporting().warn("add-accept specified without add.  add-accept will be ignored");

			isStateful = refersToSource(theReverse.getExpression(), getParentElement().getSourceName().getName())//
				|| (theEnabled != null && refersToSource(theEnabled.getExpression(), getParentElement().getSourceName().getName()))//
				|| (theAccept != null && refersToSource(theAccept.getExpression(), getParentElement().getSourceName().getName()));

			getAddOn(ExWithElementModel.Def.class).<Interpreted<?, ?, E>, SettableValue<?>> satisfyElementValueType(theTargetVariable,
				ModelTypes.Value, (interp, env) -> ModelTypes.Value.forType(interp.getTargetType()));
		}

		public static abstract class Interpreted<S, T, E extends ExElement> extends ExElement.Interpreted.Abstract<E>
		implements CompiledMapReverse.Interpreted<S, T, E> {
			private TypeToken<S> theSourceType;
			private TypeToken<T> theTargetType;
			private InterpretedValueSynth<SettableValue<?>, SettableValue<String>> theEnabled;
			private InterpretedValueSynth<SettableValue<?>, SettableValue<String>> theAccept;
			private InterpretedValueSynth<SettableValue<?>, SettableValue<S>> theAdd;
			private InterpretedValueSynth<SettableValue<?>, SettableValue<String>> theAddAccept;

			protected Interpreted(AbstractMapReverse<? super E> definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public AbstractMapReverse<? super E> getDefinition() {
				return (AbstractMapReverse<? super E>) super.getDefinition();
			}

			public TypeToken<S> getSourceType() {
				return theSourceType;
			}

			public TypeToken<T> getTargetType() {
				return theTargetType;
			}

			public abstract InterpretedValueSynth<?, ?> getReverse();

			public InterpretedValueSynth<SettableValue<?>, SettableValue<String>> getEnabled() {
				return theEnabled;
			}

			public InterpretedValueSynth<SettableValue<?>, SettableValue<String>> getAccept() {
				return theAccept;
			}

			public InterpretedValueSynth<SettableValue<?>, SettableValue<S>> getAdd() {
				return theAdd;
			}

			public InterpretedValueSynth<SettableValue<?>, SettableValue<String>> getAddAccept() {
				return theAddAccept;
			}

			@Override
			public void update(ModelComponentId sourceName, TypeToken<S> sourceType, TypeToken<T> targetType, InterpretedExpressoEnv env)
				throws ExpressoInterpretationException {
				theSourceType = sourceType;
				theTargetType = targetType;
				super.update(env);
				ModelInstanceType<SettableValue<?>, SettableValue<S>> sourceModelType = ModelTypes.Value.forType(sourceType);
				theEnabled = interpret(getDefinition().getEnabled(), ModelTypes.Value.STRING);
				theAccept = interpret(getDefinition().getAccept(), ModelTypes.Value.STRING);
				theAdd = interpret(getDefinition().getAdd(), sourceModelType);
				theAddAccept = interpret(getDefinition().getAddAccept(), ModelTypes.Value.STRING);
			}

			@Override
			public List<? extends InterpretedValueSynth<?, ?>> getComponents() {
				return BetterList.of(Stream.of(getReverse(), theEnabled, theAccept, theAdd, theAddAccept).filter(c -> c != null));
			}
		}

		public static abstract class Instantiator<S, T> implements CompiledMapReverse.Instantiator<S, T> {
			private final ModelInstantiator theLocalModel;
			private final ModelComponentId theTargetVariable;
			private final ModelValueInstantiator<SettableValue<String>> theEnabled;
			private final ModelValueInstantiator<SettableValue<String>> theAccept;
			private final ModelValueInstantiator<SettableValue<S>> theAdd;
			private final ModelValueInstantiator<SettableValue<String>> theAddAccept;
			private final boolean isStateful;
			private final ModelValueInstantiator<?> theReverse;

			protected Instantiator(ModelInstantiator localModel, ModelComponentId targetVariable,
				ModelValueInstantiator<SettableValue<String>> enabled, ModelValueInstantiator<SettableValue<String>> accept,
				ModelValueInstantiator<SettableValue<S>> add, ModelValueInstantiator<SettableValue<String>> addAccept, boolean stateful,
				ModelValueInstantiator<?> reverse) {
				theLocalModel = localModel;
				theTargetVariable = targetVariable;
				theEnabled = enabled;
				theAccept = accept;
				theAdd = add;
				theAddAccept = addAccept;
				isStateful = stateful;
				theReverse = reverse;
			}

			public ModelInstantiator getLocalModel() {
				return theLocalModel;
			}

			public ModelComponentId getTargetVariable() {
				return theTargetVariable;
			}

			public ModelValueInstantiator<SettableValue<String>> getEnabled() {
				return theEnabled;
			}

			public ModelValueInstantiator<SettableValue<String>> getAccept() {
				return theAccept;
			}

			public ModelValueInstantiator<SettableValue<S>> getAdd() {
				return theAdd;
			}

			public ModelValueInstantiator<SettableValue<String>> getAddAccept() {
				return theAddAccept;
			}

			public boolean isStateful() {
				return isStateful;
			}

			public ModelValueInstantiator<?> getReverse() {
				return theReverse;
			}

			@Override
			public void instantiate() throws ModelInstantiationException {
				theLocalModel.instantiate();
				if (theEnabled != null)
					theEnabled.instantiate();
				if (theAccept != null)
					theAccept.instantiate();
				if (theAdd != null)
					theAdd.instantiate();
				if (theAddAccept != null)
					theAddAccept.instantiate();
				theReverse.instantiate();
			}

			@Override
			public TransformReverse<S, T> reverse(SettableValue<S> sourceV, TypeToken<T> targetType,
				List<CombineWith.TransformationModification<S, T>> modifications, Transformation<S, T> transformation,
				ModelSetInstance models) throws ModelInstantiationException {
				models = theLocalModel.wrap(models);

				SettableValue<T> targetV = SettableValue.build(targetType).withValue(TypeTokens.get().getDefaultValue(targetType)).build();
				ExFlexibleElementModelAddOn.satisfyElementValue(theTargetVariable, models, targetV);
				SettableValue<String> enabledEvld = theEnabled == null ? null : theEnabled.get(models);
				SettableValue<String> acceptEvld = theAccept == null ? null : theAccept.get(models);
				SettableValue<S> addEvld = theAdd == null ? null : theAdd.get(models);
				SettableValue<String> addAcceptEvld = theAddAccept == null ? null : theAddAccept.get(models);

				Function<Transformation.TransformationValues<? extends S, ? extends T>, String> enabledFn;
				BiFunction<T, Transformation.TransformationValues<? extends S, ? extends T>, String> acceptFn;
				TriFunction<T, Transformation.TransformationValues<? extends S, ? extends T>, Boolean, S> addFn;
				BiFunction<T, Transformation.TransformationValues<? extends S, ? extends T>, String> addAcceptFn;
				enabledFn = enabledEvld == null ? null : LambdaUtils.printableFn(tvs -> {
					prepareTx(tvs, isStateful, sourceV, targetV, null, modifications);
					return enabledEvld.get();
				}, enabledEvld::toString, enabledEvld);
				acceptFn = acceptEvld == null ? null : LambdaUtils.printableBiFn((target, tvs) -> {
					prepareTx(tvs, isStateful, sourceV, targetV, target, modifications);
					return acceptEvld.get();
				}, acceptEvld::toString, acceptEvld);
				addFn = addEvld == null ? null : LambdaUtils.printableTriFn((target, tvs, test) -> {
					prepareTx(tvs, isStateful, sourceV, targetV, target, modifications);
					return addEvld.get();
				}, addEvld::toString, addEvld);
				addAcceptFn = addAcceptEvld == null ? null : LambdaUtils.printableBiFn((target, tvs) -> {
					prepareTx(tvs, isStateful, sourceV, targetV, target, modifications);
					return addAcceptEvld.get();
				}, addAcceptEvld::toString, addAcceptEvld);
				return createReverse(new ReverseParameters<>(sourceV, targetV, modifications, transformation, enabledFn, acceptFn, addFn,
					addAcceptFn, isStateful), models);
			}

			protected abstract TransformReverse<S, T> createReverse(ReverseParameters<S, T> parameters, ModelSetInstance models)
				throws ModelInstantiationException;

			/**
			 * A utility function to populate needed model values for each transformation operation. Must be static because this is invoked
			 * from the evaluated phase.
			 */
			protected static <S, T> void prepareTx(Transformation.TransformationValues<? extends S, ? extends T> tvs, boolean stateful,
				SettableValue<S> sourceV, SettableValue<T> targetV, T target,
				List<CombineWith.TransformationModification<S, T>> modifications) {
				if (stateful)
					sourceV.set(tvs.getCurrentSource(), null);
				targetV.set(target, null);
				for (CombineWith.TransformationModification<S, T> mod : modifications)
					mod.prepareTransformOperation(tvs);
			}

			@Override
			public boolean isDifferent(ModelSetInstance sourceModels, ModelSetInstance newModels) throws ModelInstantiationException {
				Object srcReversed = theReverse.get(sourceModels);
				Object newReversed = ((ModelValueInstantiator<Object>) theReverse).forModelCopy(srcReversed, sourceModels, newModels);
				if (srcReversed != newReversed)
					return true;
				if (theEnabled != null) {
					SettableValue<String> srcEnabled = theEnabled.get(sourceModels);
					SettableValue<String> newEnabled = theEnabled.forModelCopy(srcEnabled, sourceModels, newModels);
					if (srcEnabled != newEnabled)
						return true;
				}
				if (theAccept != null) {
					SettableValue<String> srcAccept = theAccept.get(sourceModels);
					SettableValue<String> newAccept = theAccept.forModelCopy(srcAccept, sourceModels, newModels);
					if (srcAccept != newAccept)
						return true;
				}
				if (theAdd != null) {
					SettableValue<S> srcAdd = theAdd.get(sourceModels);
					SettableValue<S> newAdd = theAdd.forModelCopy(srcAdd, sourceModels, newModels);
					if (srcAdd != newAdd)
						return true;
				}
				if (theAddAccept != null) {
					SettableValue<String> srcAccept = theAddAccept.get(sourceModels);
					SettableValue<String> newAccept = theAddAccept.forModelCopy(srcAccept, sourceModels, newModels);
					if (srcAccept != newAccept)
						return true;
				}
				return false;
			}
		}

		public static class ReverseParameters<S, T> {
			public final SettableValue<S> sourceValue;
			public final SettableValue<T> targetValue;
			public final List<CombineWith.TransformationModification<S, T>> modifications;
			public final Transformation<S, T> transformation;
			public final Function<Transformation.TransformationValues<? extends S, ? extends T>, String> enabledFn;
			public final BiFunction<T, Transformation.TransformationValues<? extends S, ? extends T>, String> acceptFn;
			public final TriFunction<T, Transformation.TransformationValues<? extends S, ? extends T>, Boolean, S> addFn;
			public final BiFunction<T, Transformation.TransformationValues<? extends S, ? extends T>, String> addAcceptFn;
			public final boolean stateful;

			public ReverseParameters(SettableValue<S> sourceValue, SettableValue<T> targetValue, //
				List<CombineWith.TransformationModification<S, T>> modifications, //
				Transformation<S, T> transformation, //
				Function<TransformationValues<? extends S, ? extends T>, String> enabledFn,
				BiFunction<T, TransformationValues<? extends S, ? extends T>, String> acceptFn,
				TriFunction<T, TransformationValues<? extends S, ? extends T>, Boolean, S> addFn,
				BiFunction<T, TransformationValues<? extends S, ? extends T>, String> addAcceptFn, boolean stateful) {
				this.sourceValue = sourceValue;
				this.targetValue = targetValue;
				this.modifications = modifications;
				this.transformation = transformation;
				this.enabledFn = enabledFn;
				this.acceptFn = acceptFn;
				this.addFn = addFn;
				this.addAcceptFn = addAcceptFn;
				this.stateful = stateful;
			}
		}
	}

	@ExElementTraceable(toolkit = ExpressoBaseV0_1.BASE,
		qonfigType = "replace-source",
		interpretation = SourceReplaceReverse.Interpreted.class)
	public static class SourceReplaceReverse<E extends ExElement> extends AbstractMapReverse<E> {
		private boolean isInexact;

		public SourceReplaceReverse(CompiledTransformation<?, ?, ?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType);
		}

		@Override
		public CompiledTransformation<?, ?, ?> getParentElement() {
			return super.getParentElement();
		}

		@QonfigAttributeGetter("inexact")
		public boolean isInexact() {
			return isInexact;
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			super.doUpdate(session);
			isInexact = session.getAttribute("inexact", boolean.class);
		}

		@Override
		public <S, T> Interpreted<S, T, ? extends E> interpret(ExElement.Interpreted<?> parent) throws ExpressoInterpretationException {
			return new Interpreted<>(this, parent);
		}

		public static class Interpreted<S, T, E extends ExElement> extends AbstractMapReverse.Interpreted<S, T, E> {
			private InterpretedValueSynth<SettableValue<?>, SettableValue<S>> theReverse;

			public Interpreted(SourceReplaceReverse<? super E> definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public SourceReplaceReverse<? super E> getDefinition() {
				return (SourceReplaceReverse<? super E>) super.getDefinition();
			}

			@Override
			public InterpretedValueSynth<SettableValue<?>, SettableValue<S>> getReverse() {
				return theReverse;
			}

			@Override
			public void update(ModelComponentId sourceName, TypeToken<S> sourceType, TypeToken<T> targetType, InterpretedExpressoEnv env)
				throws ExpressoInterpretationException {
				super.update(sourceName, sourceType, targetType, env);
				theReverse = interpret(getDefinition().getReverse(), ModelTypes.Value.forType(sourceType));
			}

			@Override
			public Instantiator<S, T> instantiate() throws ModelInstantiationException {
				return new Instantiator<>(getExpressoEnv().getModels().instantiate(), getDefinition().getTargetVariable(), //
					getEnabled() == null ? null : getEnabled().instantiate(), getAccept() == null ? null : getAccept().instantiate(), //
						getAdd() == null ? null : getAdd().instantiate(), getAddAccept() == null ? null : getAddAccept().instantiate(),
							getDefinition().isStateful(), getReverse().instantiate(), getDefinition().isInexact());
			}
		}

		public static class Instantiator<S, T> extends AbstractMapReverse.Instantiator<S, T> {
			private final boolean isInexact;

			public Instantiator(ModelInstantiator localModel, ModelComponentId targetVariable,
				ModelValueInstantiator<SettableValue<String>> enabled, ModelValueInstantiator<SettableValue<String>> accept,
				ModelValueInstantiator<SettableValue<S>> add, ModelValueInstantiator<SettableValue<String>> addAccept, boolean stateful,
				ModelValueInstantiator<SettableValue<S>> reverse, boolean inexact) {
				super(localModel, targetVariable, enabled, accept, add, addAccept, stateful, reverse);
				isInexact = inexact;
			}

			@Override
			public ModelValueInstantiator<SettableValue<S>> getReverse() {
				return (ModelValueInstantiator<SettableValue<S>>) super.getReverse();
			}

			@Override
			protected TransformReverse<S, T> createReverse(ReverseParameters<S, T> parameters, ModelSetInstance models)
				throws ModelInstantiationException {
				models = getLocalModel().wrap(models);
				SettableValue<S> sourceV = parameters.sourceValue;
				SettableValue<T> targetV = parameters.targetValue;
				List<CombineWith.TransformationModification<S, T>> mods = parameters.modifications;
				boolean stateful = parameters.stateful;

				SettableValue<S> reversedEvld = getReverse().get(models);
				BiFunction<T, Transformation.TransformationValues<? extends S, ? extends T>, S> reverseFn;
				reverseFn = LambdaUtils.printableBiFn((target, tvs) -> {
					prepareTx(tvs, stateful, sourceV, targetV, target, mods);
					return reversedEvld.get();
				}, reversedEvld::toString, reversedEvld);
				return new Transformation.SourceReplacingReverse<>(parameters.transformation, reverseFn, parameters.enabledFn,
					parameters.acceptFn, parameters.addFn, parameters.addAcceptFn, stateful, isInexact);
			}
		}
	}

	@ExElementTraceable(toolkit = ExpressoBaseV0_1.BASE,
		qonfigType = "modify-source",
		interpretation = SourceModifyingReverse.Interpreted.class)
	public static class SourceModifyingReverse<E extends ExElement> extends AbstractMapReverse<E> {
		public SourceModifyingReverse(CompiledTransformation<?, ?, ?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType);
		}

		@Override
		public <S, T> Interpreted<S, T, ? extends E> interpret(ExElement.Interpreted<?> parent) throws ExpressoInterpretationException {
			return new Interpreted<>(this, parent);
		}

		public static class Interpreted<S, T, E extends ExElement> extends AbstractMapReverse.Interpreted<S, T, E> {
			private InterpretedValueSynth<ObservableAction, ObservableAction> theReverse;

			public Interpreted(SourceModifyingReverse<? super E> definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public SourceModifyingReverse<? super E> getDefinition() {
				return (SourceModifyingReverse<? super E>) super.getDefinition();
			}

			@Override
			public InterpretedValueSynth<ObservableAction, ObservableAction> getReverse() {
				return theReverse;
			}

			@Override
			public void update(ModelComponentId sourceName, TypeToken<S> sourceType, TypeToken<T> targetType, InterpretedExpressoEnv env)
				throws ExpressoInterpretationException {
				super.update(sourceName, sourceType, targetType, env);
				theReverse = interpret(getDefinition().getReverse(), ModelTypes.Action.instance());
			}

			@Override
			public Instantiator<S, T> instantiate() throws ModelInstantiationException {
				return new Instantiator<>(getExpressoEnv().getModels().instantiate(), getDefinition().getTargetVariable(), //
					getEnabled() == null ? null : getEnabled().instantiate(), getAccept() == null ? null : getAccept().instantiate(), //
						getAdd() == null ? null : getAdd().instantiate(), getAddAccept() == null ? null : getAddAccept().instantiate(),
							getDefinition().isStateful(), getReverse().instantiate());
			}
		}

		public static class Instantiator<S, T> extends AbstractMapReverse.Instantiator<S, T> {
			public Instantiator(ModelInstantiator localModel, ModelComponentId targetVariable,
				ModelValueInstantiator<SettableValue<String>> enabled, ModelValueInstantiator<SettableValue<String>> accept,
				ModelValueInstantiator<SettableValue<S>> add, ModelValueInstantiator<SettableValue<String>> addAccept, boolean stateful,
				ModelValueInstantiator<ObservableAction> reverse) {
				super(localModel, targetVariable, enabled, accept, add, addAccept, stateful, reverse);
			}

			@Override
			public ModelValueInstantiator<ObservableAction> getReverse() {
				return (ModelValueInstantiator<ObservableAction>) super.getReverse();
			}

			@Override
			protected TransformReverse<S, T> createReverse(ReverseParameters<S, T> parameters, ModelSetInstance models)
				throws ModelInstantiationException {
				models = getLocalModel().wrap(models);
				SettableValue<S> sourceV = parameters.sourceValue;
				SettableValue<T> targetV = parameters.targetValue;
				List<CombineWith.TransformationModification<S, T>> mods = parameters.modifications;

				ObservableAction reversedEvld = getReverse().get(models);
				BiConsumer<T, Transformation.TransformationValues<? extends S, ? extends T>> reverseFn;
				reverseFn = (target, tvs) -> {
					prepareTx(tvs, true, sourceV, targetV, target, mods);
					reversedEvld.act(null);
				};
				return new Transformation.SourceModifyingReverse<>(reverseFn, parameters.enabledFn, parameters.acceptFn, parameters.addFn,
					parameters.addAcceptFn);
			}
		}
	}

	public static boolean refersToSource(ObservableExpression ex, String sourceName) {
		return !ex.find(ex2 -> ex2 instanceof NameExpression && ((NameExpression) ex2).getNames().getFirst().getName().equals(sourceName))
			.isEmpty();
	}

	public static abstract class ScalarOp extends ExElement.Def.Abstract<ExElement.Void> {
		private ModelComponentId theSourceAsVariable;

		protected ScalarOp(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType);
		}

		public ModelComponentId getSourceAs() {
			return theSourceAsVariable;
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			super.doUpdate(session);

			String sourceAs = getAddOnValue(ExComplexOperation.class, ExComplexOperation::getSourceAs);
			if (sourceAs != null) {
				ExWithElementModel.Def elModels = getAddOn(ExWithElementModel.Def.class);
				theSourceAsVariable = elModels.getElementValueModelId(sourceAs);
				elModels.satisfyElementValueType(theSourceAsVariable, ModelTypes.Value, (interp, env) -> {
					return ModelTypes.Value.forType(((Interpreted<?, ?>) interp).getSourceValueType());
				});
			} else
				theSourceAsVariable = null;
		}

		public abstract Interpreted<?, ?> interpret(ExElement.Interpreted<?> parent);

		public static abstract class Interpreted<S, T> extends ExElement.Interpreted.Abstract<ExElement.Void> {
			private TypeToken<S> theSourceValueType;
			private TypeToken<T> theTargetValueType;

			public Interpreted(ScalarOp definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public ScalarOp getDefinition() {
				return (ScalarOp) super.getDefinition();
			}

			public TypeToken<S> getSourceValueType() {
				return theSourceValueType;
			}

			public TypeToken<T> getTargetValueType() {
				return theTargetValueType;
			}

			protected void updateOp(InterpretedExpressoEnv env, TypeToken<S> sourceType) throws ExpressoInterpretationException {
				theSourceValueType = sourceType;
				update(env);
				theTargetValueType = evaluateTargetType();
			}

			@Override
			protected void doUpdate(InterpretedExpressoEnv expressoEnv) throws ExpressoInterpretationException {
				super.doUpdate(expressoEnv);
				theTargetValueType = getAddOnValue(ExTyped.Interpreted.class, t -> t.getValueType());
			}

			protected abstract TypeToken<T> evaluateTargetType();

			public abstract BetterList<InterpretedValueSynth<?, ?>> getComponents();

			public abstract Instantiator<S, T> instantiate() throws ModelInstantiationException;
		}

		public static abstract class Instantiator<S, T> {
			private final ModelInstantiator theLocalModels;
			private final ModelComponentId theSourceAsVariable;

			Instantiator(ScalarOp.Interpreted<S, T> interpreted) {
				if (interpreted.getModels() != interpreted.getParentElement().getModels()) {
					theLocalModels = interpreted.getModels().instantiate();
					theSourceAsVariable = interpreted.getDefinition().getSourceAs();
				} else {
					theLocalModels = null;
					theSourceAsVariable = null;
				}
			}

			public void instantiate() throws ModelInstantiationException {
				if (theLocalModels != null)
					theLocalModels.instantiate();
			}

			public SettableValue<T> get(SettableValue<S> source, ModelSetInstance models) throws ModelInstantiationException {
				if (theLocalModels != null) {
					models = theLocalModels.wrap(models);
					if (theSourceAsVariable != null)
						ExFlexibleElementModelAddOn.satisfyElementValue(theSourceAsVariable, models, source);
				}
				return doGet(source, models);
			}

			public boolean isDifferent(ModelSetInstance sourceModels, ModelSetInstance newModels) throws ModelInstantiationException {
				if (theLocalModels != null) {
					sourceModels = theLocalModels.wrap(sourceModels);
					newModels = theLocalModels.wrap(newModels);
				}
				return checkIsDifferent(sourceModels, newModels);
			}

			protected abstract SettableValue<T> doGet(SettableValue<S> source, ModelSetInstance models) throws ModelInstantiationException;

			protected abstract boolean checkIsDifferent(ModelSetInstance sourceModels, ModelSetInstance newModels)
				throws ModelInstantiationException;
		}
	}

	@ExElementTraceable(toolkit = ExpressoBaseV0_1.BASE, qonfigType = If.IF, interpretation = If.Interpreted.class)
	public static class If extends ScalarOp {
		public static final String IF = "if";

		private CompiledExpression theValue;
		private final List<ScalarOp> theIfs;

		public If(Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType);
			theIfs = new ArrayList<>();
		}

		@QonfigAttributeGetter()
		public CompiledExpression getValue() {
			return theValue;
		}

		@QonfigChildGetter("if")
		public List<ScalarOp> getIfs() {
			return Collections.unmodifiableList(theIfs);
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			super.doUpdate(session);
			theValue = getValueExpression(session);
			syncChildren(ScalarOp.class, theIfs, session.forChildren("if"));
		}

		@Override
		public Interpreted<?, ?> interpret(ExElement.Interpreted<?> parent) {
			return new Interpreted<>(this, parent);
		}

		public static class Interpreted<S, T> extends ScalarOp.Interpreted<S, T> {
			private InterpretedValueSynth<SettableValue<?>, SettableValue<T>> theValue;
			private final List<ScalarOp.Interpreted<S, T>> theIfs;

			Interpreted(If definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
				theIfs = new ArrayList<>();
			}

			@Override
			public If getDefinition() {
				return (If) super.getDefinition();
			}

			public InterpretedValueSynth<SettableValue<?>, SettableValue<T>> getValue() {
				return theValue;
			}

			public List<ScalarOp.Interpreted<S, T>> getIfs() {
				return Collections.unmodifiableList(theIfs);
			}

			@Override
			protected void doUpdate(InterpretedExpressoEnv expressoEnv) throws ExpressoInterpretationException {
				super.doUpdate(expressoEnv);

				theValue = interpret(getDefinition().getValue(),
					getTargetValueType() == null ? ModelTypes.Value.anyAsV() : ModelTypes.Value.forType(getTargetValueType()));
				syncChildren(getDefinition().getIfs(), theIfs, iff -> (ScalarOp.Interpreted<S, T>) iff.interpret(this),
					(iff, env) -> iff.updateOp(env, getSourceValueType()));
			}

			@Override
			protected TypeToken<T> evaluateTargetType() {
				TypeToken<? extends T>[] types = new TypeToken[theIfs.size() + 1];
				types[0] = (TypeToken<? extends T>) theValue.getType().getType(0);
				for (int i = 0; i < theIfs.size(); i++)
					types[i + 1] = theIfs.get(i).getTargetValueType();
				return TypeTokens.get().getCommonType(types);
			}

			@Override
			public BetterList<InterpretedValueSynth<?, ?>> getComponents() {
				return BetterList.of(Stream.concat(Stream.of(theValue), //
					BetterList.of(theIfs.stream(), iff -> iff.getComponents().stream()).stream()));
			}

			@Override
			public ScalarOp.Instantiator<S, T> instantiate() throws ModelInstantiationException {
				return new Instantiator<>(this);
			}
		}

		protected static class Instantiator<S, T> extends ScalarOp.Instantiator<S, T> {
			private final TypeToken<T> theTargetType;
			private final ModelValueInstantiator<SettableValue<T>> theValue;
			private final List<ModelValueInstantiator<SettableValue<Boolean>>> theIfConditions;
			private final List<ScalarOp.Instantiator<S, T>> theIfs;

			protected Instantiator(Interpreted<S, T> interpreted) throws ModelInstantiationException {
				super(interpreted);
				theTargetType = interpreted.getTargetValueType();
				theValue=interpreted.getValue().instantiate();
				theIfConditions = new ArrayList<>(interpreted.getIfs().size());
				theIfs = new ArrayList<>(interpreted.getIfs().size());
				for (ScalarOp.Interpreted<S, T> iff : interpreted.getIfs()) {
					theIfConditions.add(iff.getAddOn(IfOp.Interpreted.class).getIf().instantiate());
					theIfs.add(iff.instantiate());
				}
			}

			@Override
			public void instantiate() throws ModelInstantiationException {
				super.instantiate();
				theValue.instantiate();
				for (ScalarOp.Instantiator<S, T> iff : theIfs)
					iff.instantiate();
			}

			@Override
			protected SettableValue<T> doGet(SettableValue<S> source, ModelSetInstance models) throws ModelInstantiationException {
				List<ObservableValue<ConditionalValue<T>>> ifs = new ArrayList<>(theIfs.size() + 1);
				for (int i = 0; i < theIfs.size(); i++) {
					SettableValue<Boolean> ifCondition = theIfConditions.get(i).get(models);
					SettableValue<T> iff = theIfs.get(i).get(source, models);
					ifs.add(ifCondition
						.map(LambdaUtils.printableFn(b -> new ConditionalValue<>(Boolean.TRUE.equals(b), iff),
							() -> "condition(" + iff + ")", null)));
				}
				SettableValue<T> value = theValue.get(models);
				ifs.add(ObservableValue.of(new ConditionalValue<>(true, value)));
				ObservableValue<ConditionalValue<T>> firstTrue = ObservableValue.<ConditionalValue<T>> firstValue(//
					(TypeToken<ConditionalValue<T>>) (TypeToken<?>) TypeTokens.get().of(ConditionalValue.class),
					LambdaUtils.printablePred(cv -> cv.condition, "condition", null), null,
					ifs.toArray(new ObservableValue[ifs.size()]));
				return SettableValue.flatten(firstTrue
					.map(TypeTokens.get().keyFor(SettableValue.class).<SettableValue<T>> parameterized(theTargetType),
						LambdaUtils.printableFn(cv -> cv.value, "value", null)));
			}

			@Override
			protected boolean checkIsDifferent(ModelSetInstance sourceModels, ModelSetInstance models) throws ModelInstantiationException {
				SettableValue<T> value = theValue.get(sourceModels);
				if (value != theValue.forModelCopy(value, sourceModels, models))
					return true;
				for (ScalarOp.Instantiator<S, T> iff : theIfs) {
					if (iff.isDifferent(sourceModels, models))
						return true;
				}
				return false;
			}
		}

		static class ConditionalValue<T> {
			final boolean condition;
			final SettableValue<T> value;

			ConditionalValue(boolean condition, SettableValue<T> value) {
				this.condition = condition;
				this.value = value;
			}

			@Override
			public String toString() {
				return value.toString();
			}
		}
	}

	@ExElementTraceable(toolkit = ExpressoBaseV0_1.BASE, qonfigType = IfOp.IF_OP, interpretation = IfOp.Interpreted.class)
	public static class IfOp extends ExAddOn.Def.Abstract<ExElement.Void, ExAddOn.Void<ExElement.Void>> {
		public static final String IF_OP = "if-op";

		private CompiledExpression theIf;

		public IfOp(QonfigAddOn type, ExElement.Def<?> element) {
			super(type, element);
		}

		@QonfigAttributeGetter("if")
		public CompiledExpression getIf() {
			return theIf;
		}

		@Override
		public void update(ExpressoQIS session, ExElement.Def<? extends Void> element) throws QonfigInterpretationException {
			super.update(session, element);

			theIf = getElement().getAttributeExpression("if", session);
		}

		@Override
		public Interpreted interpret(ExElement.Interpreted<?> element) {
			return new Interpreted(this, element);
		}

		public static class Interpreted extends ExAddOn.Interpreted.Abstract<ExElement.Void, ExAddOn.Void<ExElement.Void>> {
			private InterpretedValueSynth<SettableValue<?>, SettableValue<Boolean>> theIf;

			Interpreted(IfOp definition, ExElement.Interpreted<?> element) {
				super(definition, element);
			}

			@Override
			public IfOp getDefinition() {
				return (IfOp) super.getDefinition();
			}

			public InterpretedValueSynth<SettableValue<?>, SettableValue<Boolean>> getIf() {
				return theIf;
			}

			@Override
			public void update(ExElement.Interpreted<? extends Void> element) throws ExpressoInterpretationException {
				super.update(element);

				theIf = getElement().interpret(getDefinition().getIf(), ModelTypes.Value.BOOLEAN);
			}

			@Override
			public Class<ExAddOn.Void<ExElement.Void>> getInstanceType() {
				return (Class<ExAddOn.Void<ExElement.Void>>) (Class<?>) ExAddOn.Void.class;
			}

			@Override
			public ExAddOn.Void<Void> create(Void element) {
				return null;
			}
		}
	}

	@ExElementTraceable(toolkit = ExpressoBaseV0_1.BASE, qonfigType = Switch.SWITCH, interpretation = Switch.Interpreted.class)
	public static class Switch extends ScalarOp {
		public static final String SWITCH = "switch";

		private CompiledExpression theDefault;
		private final List<ScalarOp> theCases;

		public Switch(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType);
			theCases = new ArrayList<>();
		}

		@QonfigAttributeGetter("default")
		public CompiledExpression getDefault() {
			return theDefault;
		}

		@QonfigChildGetter("case")
		public List<ScalarOp> getCases() {
			return Collections.unmodifiableList(theCases);
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			super.doUpdate(session);

			theDefault = getAttributeExpression("default", session);
			syncChildren(ScalarOp.class, theCases, session.forChildren("case"));
		}

		@Override
		public Interpreted<?, ?> interpret(ExElement.Interpreted<?> parent) {
			return new Interpreted<>(this, parent);
		}

		public static class Interpreted<S, T> extends ScalarOp.Interpreted<S, T> {
			private InterpretedValueSynth<SettableValue<?>, SettableValue<T>> theDefault;
			private final List<ScalarOp.Interpreted<S, T>> theCases;

			Interpreted(Switch definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
				theCases = new ArrayList<>();
			}

			@Override
			public Switch getDefinition() {
				return (Switch) super.getDefinition();
			}

			public InterpretedValueSynth<SettableValue<?>, SettableValue<T>> getDefault() {
				return theDefault;
			}

			public List<ScalarOp.Interpreted<S, T>> getCases() {
				return Collections.unmodifiableList(theCases);
			}

			@Override
			protected void doUpdate(InterpretedExpressoEnv expressoEnv) throws ExpressoInterpretationException {
				super.doUpdate(expressoEnv);

				theDefault = interpret(getDefinition().getDefault(), //
					getTargetValueType() == null ? ModelTypes.Value.anyAsV() : ModelTypes.Value.forType(getTargetValueType()));
				syncChildren(getDefinition().getCases(), theCases, def -> (ScalarOp.Interpreted<S, T>) def.interpret(this),
					(interp, env) -> interp.updateOp(env, getSourceValueType()));
			}

			@Override
			protected TypeToken<T> evaluateTargetType() {
				TypeToken<? extends T>[] types = new TypeToken[theCases.size() + 1];
				types[0] = (TypeToken<? extends T>) theDefault.getType().getType(0);
				for (int i = 0; i < theCases.size(); i++)
					types[i + 1] = theCases.get(i).getTargetValueType();
				return TypeTokens.get().getCommonType(types);
			}

			@Override
			public BetterList<InterpretedValueSynth<?, ?>> getComponents() {
				return BetterList.of(Stream.concat(Stream.of(theDefault), //
					BetterList.of(theCases.stream(), iff -> iff.getComponents().stream()).stream()));
			}

			@Override
			public ScalarOp.Instantiator<S, T> instantiate() throws ModelInstantiationException {
				return new Instantiator<>(this);
			}
		}

		static class Instantiator<S, T> extends ScalarOp.Instantiator<S, T> {
			private final TypeToken<T> theTargetType;
			private final ModelValueInstantiator<SettableValue<T>> theDefault;
			private final List<ModelValueInstantiator<SettableValue<S>>> theCaseValues;
			private final List<ScalarOp.Instantiator<S, T>> theCases;

			Instantiator(Interpreted<S, T> interpreted) throws ModelInstantiationException {
				super(interpreted);
				theTargetType = interpreted.getTargetValueType();
				theDefault = interpreted.getDefault().instantiate();
				theCaseValues = new ArrayList<>(interpreted.getCases().size());
				theCases = new ArrayList<>(interpreted.getCases().size());
				for (ScalarOp.Interpreted<S, T> caase : interpreted.getCases()) {
					theCaseValues.add(caase.getAddOn(CaseOp.Interpreted.class).getCase().instantiate());
					theCases.add(caase.instantiate());
				}
			}

			@Override
			public void instantiate() throws ModelInstantiationException {
				super.instantiate();
				theDefault.instantiate();
				for (ScalarOp.Instantiator<S, T> caase : theCases)
					caase.instantiate();
			}

			@Override
			protected SettableValue<T> doGet(SettableValue<S> source, ModelSetInstance models) throws ModelInstantiationException {
				List<ObservableValue<CaseValue<S, T>>> caseTuples = new ArrayList<>(theCases.size());
				for (int i = 0; i < theCases.size(); i++) {
					SettableValue<S> caseValue = theCaseValues.get(i).get(models);
					SettableValue<T> value = theCases.get(i).get(source, models);
					caseTuples.add(caseValue.<CaseValue<S, T>> map(cv -> new CaseValue<>(cv, value)));
				}
				TypeToken<CaseValue<S, T>> caseValueType = TypeTokens.get().keyFor(CaseValue.class)//
					.<CaseValue<S, T>> parameterized((TypeToken<S>) (TypeToken<?>) TypeTokens.get().OBJECT, theTargetType);
				TypeToken<SettableValue<T>> settableValueType = TypeTokens.get().keyFor(SettableValue.class)
					.<SettableValue<T>> parameterized(theTargetType);
				ObservableMap<S, SettableValue<T>> map = ObservableCollection.of(TypeTokens.get().keyFor(ObservableValue.class)//
					.<ObservableValue<CaseValue<S, T>>> parameterized(caseValueType), caseTuples)//
					.flow()//
					.flattenValues(caseValueType, LambdaUtils.identity())//
					.groupBy((TypeToken<S>) (TypeToken<?>) TypeTokens.get().OBJECT, cv -> cv.caseValue, null)//
					.withValues(values -> values.map(settableValueType, cv -> cv.value))//
					.gatherActive(models.getUntil())//
					.singleMap(true);

				ObservableValue<ObservableValue<SettableValue<T>>> mapValue = source.map(s -> map.observe(s));
				SettableValue<T> def = theDefault.get(models);
				ObservableValue<SettableValue<T>> caseOrDefault = ObservableValue.firstValue(settableValueType, sv -> sv != null, null,
					ObservableValue.flatten(mapValue), ObservableValue.of(settableValueType, def));
				return SettableValue.flatten(caseOrDefault);
			}

			@Override
			protected boolean checkIsDifferent(ModelSetInstance sourceModels, ModelSetInstance models) throws ModelInstantiationException {
				SettableValue<T> value = theDefault.get(sourceModels);
				if (value != theDefault.forModelCopy(value, sourceModels, models))
					return true;
				for (ScalarOp.Instantiator<S, T> iff : theCases) {
					if (iff.isDifferent(sourceModels, models))
						return true;
				}
				return false;
			}
		}

		static class CaseValue<S, T> {
			final S caseValue;
			final SettableValue<T> value;

			CaseValue(S caseValue, SettableValue<T> value) {
				this.caseValue = caseValue;
				this.value = value;
			}

			@Override
			public String toString() {
				return caseValue + "=" + value;
			}
		}
	}

	@ExElementTraceable(toolkit = ExpressoBaseV0_1.BASE, qonfigType = CaseOp.CASE_OP, interpretation = CaseOp.Interpreted.class)
	public static class CaseOp extends ExAddOn.Def.Abstract<ExElement.Void, ExAddOn.Void<ExElement.Void>> {
		public static final String CASE_OP = "case-op";

		private CompiledExpression theCase;

		public CaseOp(QonfigAddOn type, ExElement.Def<?> element) {
			super(type, element);
		}

		@QonfigAttributeGetter("case")
		public CompiledExpression getCase() {
			return theCase;
		}

		@Override
		public void update(ExpressoQIS session, ExElement.Def<? extends Void> element) throws QonfigInterpretationException {
			super.update(session, element);

			theCase = getElement().getAttributeExpression("case", session);
		}

		@Override
		public Interpreted<?> interpret(ExElement.Interpreted<?> element) {
			return new Interpreted(this, element);
		}

		public static class Interpreted<S> extends ExAddOn.Interpreted.Abstract<ExElement.Void, ExAddOn.Void<ExElement.Void>> {
			private InterpretedValueSynth<SettableValue<?>, SettableValue<S>> theCase;

			Interpreted(CaseOp definition, ExElement.Interpreted<?> element) {
				super(definition, element);
			}

			@Override
			public CaseOp getDefinition() {
				return (CaseOp) super.getDefinition();
			}

			public InterpretedValueSynth<SettableValue<?>, SettableValue<S>> getCase() {
				return theCase;
			}

			@Override
			public void update(ExElement.Interpreted<? extends Void> element) throws ExpressoInterpretationException {
				super.update(element);

				theCase = getElement().interpret(getDefinition().getCase(),
					ModelTypes.Value.forType(((ScalarOp.Interpreted<S, ?>) element).getSourceValueType()));
			}

			@Override
			public Class<ExAddOn.Void<ExElement.Void>> getInstanceType() {
				return (Class<ExAddOn.Void<ExElement.Void>>) (Class<?>) ExAddOn.Void.class;
			}

			@Override
			public ExAddOn.Void<Void> create(Void element) {
				return null;
			}
		}
	}

	@ExElementTraceable(toolkit = ExpressoBaseV0_1.BASE,
		qonfigType = Return.RETURN,
		interpretation = Return.Interpreted.class)
	static class Return extends ScalarOp {
		public static final String RETURN = "return";

		private CompiledExpression theValue;

		public Return(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType);
		}

		@QonfigAttributeGetter()
		public CompiledExpression getValue() {
			return theValue;
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			super.doUpdate(session);
			theValue = getValueExpression(session);
		}

		@Override
		public Interpreted<?, ?> interpret(ExElement.Interpreted<?> parent) {
			return new Interpreted<>(this, parent);
		}

		public static class Interpreted<S, T> extends ScalarOp.Interpreted<S, T> {
			private InterpretedValueSynth<SettableValue<?>, SettableValue<T>> theValue;

			public Interpreted(Return definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public Return getDefinition() {
				return (Return) super.getDefinition();
			}

			public InterpretedValueSynth<SettableValue<?>, SettableValue<T>> getValue() {
				return theValue;
			}

			@Override
			protected void doUpdate(InterpretedExpressoEnv expressoEnv) throws ExpressoInterpretationException {
				super.doUpdate(expressoEnv);

				theValue = interpret(getDefinition().getValue(),
					getTargetValueType() == null ? ModelTypes.Value.anyAsV() : ModelTypes.Value.forType(getTargetValueType()));
			}

			@Override
			protected TypeToken<T> evaluateTargetType() {
				return (TypeToken<T>) theValue.getType().getType(0);
			}

			@Override
			public BetterList<InterpretedValueSynth<?, ?>> getComponents() {
				return BetterList.of(theValue);
			}

			@Override
			public ScalarOp.Instantiator<S, T> instantiate() throws ModelInstantiationException {
				return new Instantiator<>(this);
			}
		}

		static class Instantiator<S, T> extends ScalarOp.Instantiator<S, T> {
			private final ModelValueInstantiator<SettableValue<T>> theValue;

			Instantiator(Interpreted<S, T> interpreted) throws ModelInstantiationException {
				super(interpreted);
				theValue = interpreted.getValue().instantiate();
			}

			@Override
			public void instantiate() throws ModelInstantiationException {
				super.instantiate();
				theValue.instantiate();
			}

			@Override
			protected SettableValue<T> doGet(SettableValue<S> source, ModelSetInstance models) throws ModelInstantiationException {
				return theValue.get(models);
			}

			@Override
			protected boolean checkIsDifferent(ModelSetInstance sourceModels, ModelSetInstance newModels)
				throws ModelInstantiationException {
				SettableValue<T> value = theValue.get(sourceModels);
				return value != theValue.forModelCopy(value, sourceModels, newModels);
			}
		}
	}
}
