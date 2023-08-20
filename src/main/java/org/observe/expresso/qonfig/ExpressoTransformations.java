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
import org.observe.SettableValue;
import org.observe.Transformation;
import org.observe.Transformation.MaybeReversibleTransformation;
import org.observe.Transformation.ReversibleTransformationBuilder;
import org.observe.Transformation.TransformReverse;
import org.observe.Transformation.TransformationValues;
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
import org.observe.expresso.qonfig.ElementTypeTraceability.SingleTypeTraceability;
import org.observe.expresso.qonfig.ExElement.Def;
import org.observe.expresso.qonfig.ExpressoTransformations.CombineWith.TransformationModification;
import org.observe.util.TypeTokens;
import org.qommons.LambdaUtils;
import org.qommons.Named;
import org.qommons.TriFunction;
import org.qommons.collect.BetterList;
import org.qommons.collect.CollectionUtils;
import org.qommons.config.AbstractQIS;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.config.QonfigInterpreterCore;

import com.google.common.reflect.TypeToken;

public class ExpressoTransformations {
	public static final String TARGET_MODEL_TYPE_KEY = "Expresso.Transformation.Target.Model.Type";

	private ExpressoTransformations() {
	}

	public static class ExpressoTransformedElement<M1, M2> extends ExElement.Def.Abstract<ModelValueElement<M2, ?>>
	implements ModelValueElement.CompiledSynth<M2, ModelValueElement<M2, ?>> {
		private static final SingleTypeTraceability<ModelValueElement<?, ?>, Interpreted<?, ?, ?, ?>, ExpressoTransformedElement<?, ?>> TRACEABILITY = ElementTypeTraceability
			.getElementTraceability(ExpressoBaseV0_1.NAME, ExpressoBaseV0_1.VERSION, "transform", ExpressoTransformedElement.class,
				Interpreted.class, null);

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
			withTraceability(TRACEABILITY.validate(session.getFocusType(), session.reporting()));
			withTraceability(ModelValueElement.TRACEABILITY.validate(session.getFocusType().getSuperElement(), session.reporting()));
			super.doUpdate(session);
			theModelPath = session.get(ExpressoBaseV0_1.PATH_KEY, String.class);
			theSource = session.getAttributeExpression("source");
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
		public Interpreted<M1, ?, M2, ?> interpret() {
			return new Interpreted<>(this);
		}

		public static class Interpreted<M1, MV1 extends M1, M2, MV2 extends M2>
		extends ExElement.Interpreted.Abstract<ModelValueElement<M2, MV2>>
		implements ModelValueElement.InterpretedSynth<M2, MV2, ModelValueElement<M2, MV2>> {
			private InterpretedValueSynth<M1, MV1> theSource;
			private final List<Operation.Interpreted<?, ?, ?, ?, ?>> theOperations;

			public Interpreted(ExpressoTransformedElement<M1, M2> definition) {
				super(definition, null);
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
					theSource = getDefinition().getSource()
						.interpret(((ModelType<M1>) getDefinition().getSource().getModelType()).<MV1> anyAs(), getExpressoEnv());
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
			public ModelValueInstantiator<MV2> instantiate() {
				List<Operation.Instantiator<MV1, MV2>> operations = new ArrayList<>(theOperations.size());
				boolean efficientCopy = true;
				TransformInstantiator<M1, ?> fullTransform = TransformInstantiator.unity();
				for (Operation.Interpreted<?, ?, ?, ?, ?> op : theOperations) {
					Operation.Instantiator<?, ?> opInst = op.instantiate();
					if (efficientCopy)
						efficientCopy = opInst instanceof Operation.EfficientCopyingInstantiator
						&& ((Operation.EfficientCopyingInstantiator<?, ?>) opInst).isEfficientCopy();
					fullTransform = ((Operation.Instantiator<Object, ?>) opInst)
						.after((TransformInstantiator<M1, Object>) fullTransform);
				}
				return new Instantiator<>(theSource.instantiate(), Collections.unmodifiableList(operations), efficientCopy,
					(TransformInstantiator<MV1, MV2>) fullTransform);
			}

			@Override
			public ModelValueElement<M2, MV2> create(ExElement parent, ModelSetInstance models) throws ModelInstantiationException {
				return null;
			}
		}

		static class Instantiator<MV1, MV2> implements ModelValueInstantiator<MV2> {
			private final ModelValueInstantiator<MV1> theSource;
			private final List<Operation.Instantiator<MV1, MV2>> theOperations;
			private final boolean isEfficientCopy;
			private final TransformInstantiator<MV1, MV2> theFullTransform;

			public Instantiator(ModelValueInstantiator<MV1> source, List<Operation.Instantiator<MV1, MV2>> operations,
				boolean efficientCopy, TransformInstantiator<MV1, MV2> fullTransform) {
				theSource = source;
				theOperations = operations;
				isEfficientCopy = efficientCopy;
				theFullTransform = fullTransform;
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
						chain[i] = ((Operation.EfficientCopyingInstantiator<?, Object>) theOperations.get(i))
							.getSource(target);
						target = chain[i];
					}
					target = chain[0];
					for (int i = 0; i < theOperations.size(); i++) {
						Object sourceValue = target;
						target = chain[i + 1];
						target = ((Operation.EfficientCopyingInstantiator<Object, Object>) theOperations.get(i))
							.forModelCopy(target, sourceValue, sourceModels, newModels);
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
		Interpreted<M1, ?, M2, ?, ? extends E> interpret(ExElement.Interpreted<?> parent)
			throws ExpressoInterpretationException;

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

			Instantiator<MV1, MV2> instantiate();
		}

		public interface Instantiator<MV1, MV2> extends TransformInstantiator<MV1, MV2> {
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

		static <T> TransformInstantiator<T, T> unity() {
			return (v, models) -> v;
		}

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
	public interface ActionTransform<M, E extends ExElement> extends Operation<ObservableAction<?>, M, E> {
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

			protected Interpreted(Def<? super ExElement> definition, org.observe.expresso.qonfig.ExElement.Interpreted<?> parent) {
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
		InterpretedExpressoEnv env, boolean preferMessage) throws ExpressoInterpretationException {
		InterpretedValueSynth<SettableValue<?>, SettableValue<String>> test;
		try {
			if (preferMessage)
				test = testX.interpret(ModelTypes.Value.forType(String.class), env);
			else {
				test = testX.interpret(ModelTypes.Value.forType(boolean.class), env)//
					.mapValue(ModelTypes.Value.forType(String.class),
						bv -> SettableValue.asSettable(bv.map(String.class, b -> b ? null : "Not allowed"), __ -> "Not settable"));
			}
		} catch (ExpressoInterpretationException e) {
			try {
				if (preferMessage) {
					test = testX.interpret(ModelTypes.Value.forType(boolean.class), env)//
						.mapValue(ModelTypes.Value.forType(String.class),
							bv -> SettableValue.asSettable(bv.map(String.class, b -> b ? null : "Not allowed"), __ -> "Not settable"));
				} else
					test = testX.interpret(ModelTypes.Value.forType(String.class), env);
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
			Instantiator<S, T, MV1, MV2> instantiate();
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

			Instantiator<S, T> instantiate();
		}

		public interface Instantiator<S, T> {
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

	public static abstract class AbstractCompiledTransformation<M1, M2, E extends ExElement> extends ExElement.Def.Abstract<E>
	implements CompiledTransformation<M1, M2, E> {
		private static final SingleTypeTraceability<ExElement, Interpreted<?, ?, ?, ?, ?, ?, ?>, AbstractCompiledTransformation<?, ?, ?>> TRACEABILITY = ElementTypeTraceability
			.getElementTraceability(ExpressoBaseV0_1.NAME, ExpressoBaseV0_1.VERSION, "map-to", AbstractCompiledTransformation.class,
				Interpreted.class, null);

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

		@QonfigAttributeGetter("cache")
		public boolean isCached() {
			return isCached;
		}

		@QonfigAttributeGetter("re-eval-on-update")
		public boolean isReEvalOnUpdate() {
			return isReEvalOnUpdate;
		}

		@QonfigAttributeGetter("fire-if-unchanged")
		public boolean isFireIfUnchanged() {
			return isFireIfUnchanged;
		}

		@QonfigAttributeGetter("null-to-null")
		public boolean isNullToNull() {
			return isNullToNull;
		}

		@QonfigAttributeGetter("many-to-one")
		public boolean isManyToOne() {
			return isManyToOne;
		}

		@QonfigAttributeGetter("one-to-many")
		public boolean isOneToMany() {
			return isOneToMany;
		}

		@QonfigChildGetter("map")
		public MapWith<?> getMapWith() {
			return theMapWith;
		}

		@QonfigAttributeGetter("equivalence")
		public CompiledExpression getEquivalence() {
			return theEquivalence;
		}

		@QonfigAttributeGetter("combined-value")
		public List<CombineWith<?>> getCombinedValues() {
			return Collections.unmodifiableList(theCombinedValues);
		}

		@QonfigChildGetter("reverse")
		public CompiledMapReverse<?> getReverse() {
			return theReverse;
		}

		@Override
		public void update(ExpressoQIS session, ModelType<M1> sourceModelType) throws QonfigInterpretationException {
			withTraceability(TRACEABILITY.validate(session.getFocusType(), session.reporting()));
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

			theMapWith = ExElement.useOrReplace(MapWith.class, theMapWith, session, "map");
			theEquivalence = session.getAttributeExpression("equivalence");
			ExElement.syncDefs(CombineWith.class, theCombinedValues, session.forChildren("combined-value"));
			theReverse = ExElement.useOrReplace(CompiledMapReverse.class, theReverse, session, "reverse");

			for (CombineWith<?> combine : theCombinedValues)
				withElModel.satisfyElementValueType(combine.getName(), ModelTypes.Value, //
					(interp, env) -> combine.getElementValue().interpret(ModelTypes.Value.any(), env).getType());
			withElModel.<Interpreted<M1, ?, ?, ?, M2, ?, E>, SettableValue<?>> satisfyElementValueType(theSourceName.getName(),
				ModelTypes.Value, //
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
				CollectionUtils
				.synchronize(theCombinedValues, getDefinition().getCombinedValues(), (i, d) -> i.getIdentity() == d.getIdentity())//
				.simpleE(d -> d.interpret(this))//
				.onLeftX(el -> el.getLeftValue().destroy())//
				.onRightX(el -> el.getLeftValue().update(getExpressoEnv()))//
				.onCommonX(el -> el.getLeftValue().update(getExpressoEnv()))//
				.adjust();
				if (theMapWith == null || theMapWith.getIdentity() != getDefinition().getMapWith().getIdentity())
					theMapWith = getDefinition().getMapWith().interpret(this);
				theMapWith.update(theSourceType, getExpressoEnv());
				if (getDefinition().getEquivalence() == null)
					theEquivalence = null;
				else {
					theEquivalence = getDefinition().getEquivalence()
						.interpret(ModelTypes.Value.forType(TypeTokens.get().keyFor(Equivalence.class)
							.<Equivalence<? super T>> parameterized(TypeTokens.get().getSuperWildcard(theMapWith.getTargetType()))),
							getExpressoEnv());
				}
				if (getDefinition().getReverse() == null) {
					if (theReverse != null)
						theReverse.destroy();
					theReverse = null;
				} else if (theReverse == null || theReverse.getIdentity() != getDefinition().getReverse().getIdentity()) {
					if (theReverse != null)
						theReverse.destroy();
					theReverse = getDefinition().getReverse().interpret(this);
				}
				if (theReverse != null)
					theReverse.update(getDefinition().getSourceName(), theMapWith.getSourceType(), theMapWith.getTargetType(),
						getExpressoEnv());
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

	public static class MapWith<E extends ExElement> extends ExElement.Def.Abstract<E> {
		private CompiledExpression theMap;

		public MapWith(AbstractCompiledTransformation<?, ?, ?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType);
		}

		@Override
		public AbstractCompiledTransformation<?, ?, ?> getParentElement() {
			return (AbstractCompiledTransformation<?, ?, ?>) super.getParentElement();
		}

		public CompiledExpression getMap() {
			return theMap;
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			super.doUpdate(session);
			theMap = session.getValueExpression();
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
					theMap = getDefinition().getMap()//
					.interpret(//
						ModelTypes.Value.forType(getParentElement().getEvaluatedTargetType()), getExpressoEnv());
				else
					theMap = getDefinition().getMap()//
					.interpret(//
						ModelTypes.Value.<SettableValue<T>> anyAs(), getExpressoEnv());
				isTesting = env.isTesting();
			}

			public BetterList<InterpretedValueSynth<?, ?>> getComponents() {
				return BetterList.of(theMap);
			}

			public Instantiator<S, T> instantiate() {
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

		@Override
		public CompiledExpression getElementValue() {
			return theValue;
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			super.doUpdate(session);
			theValue = session.getValueExpression();
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
				theValue = getDefinition().getElementValue().interpret(ModelTypes.Value.<SettableValue<T>> anyAs(), getExpressoEnv());
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

			public Instantiator<T> instantiate() {
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

			public <S, T2> ReversibleTransformationBuilder<S, T2, ?> addTo(ReversibleTransformationBuilder<S, T2, ?> builder,
				ModelSetInstance models, Consumer<? super TransformationModification<S, T2>> modify)
					throws ModelInstantiationException {
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

	public static abstract class AbstractMapReverse<E extends ExElement> extends ExElement.Def.Abstract<E>
	implements CompiledMapReverse<E> {
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

		public ModelComponentId getTargetVariable() {
			return theTargetVariable;
		}

		public CompiledExpression getReverse() {
			return theReverse;
		}

		public CompiledExpression getEnabled() {
			return theEnabled;
		}

		public CompiledExpression getAccept() {
			return theAccept;
		}

		public CompiledExpression getAdd() {
			return theAdd;
		}

		public CompiledExpression getAddAccept() {
			return theAddAccept;
		}

		public boolean isStateful() {
			return isStateful;
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			super.doUpdate(session);
			String targetName = session.getAttributeText("target-as");
			theTargetVariable = getAddOn(ExWithElementModel.Def.class).getElementValueModelId(targetName);
			theReverse = session.getValueExpression();
			theEnabled = session.getAttributeExpression("enabled");
			theAccept = session.getAttributeExpression("accept");
			theAdd = session.getAttributeExpression("add");
			theAddAccept = session.getAttributeExpression("add-accept");

			if (theAdd == null && theAddAccept != null)
				reporting().warn("add-accept specified without add.  add-accept will be ignored");

			isStateful = refersToSource(theReverse.getExpression(), getParentElement().getSourceName().getName())//
				|| (theEnabled != null && refersToSource(theEnabled.getExpression(), getParentElement().getSourceName().getName()))//
				|| (theAccept != null && refersToSource(theAccept.getExpression(), getParentElement().getSourceName().getName()));

			getAddOn(ExWithElementModel.Def.class).<Interpreted<?, ?, E>, SettableValue<?>> satisfyElementValueType(
				theTargetVariable.getName(),
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
				theEnabled = getDefinition().getEnabled() == null ? null
					: getDefinition().getEnabled().interpret(ModelTypes.Value.STRING, getExpressoEnv());
				theAccept = getDefinition().getAccept() == null ? null
					: getDefinition().getAccept().interpret(ModelTypes.Value.STRING, getExpressoEnv());
				theAdd = getDefinition().getAdd() == null ? null : getDefinition().getAdd().interpret(sourceModelType, getExpressoEnv());
				theAddAccept = getDefinition().getAddAccept() == null ? null
					: getDefinition().getAddAccept().interpret(ModelTypes.Value.STRING, getExpressoEnv());
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
					return acceptEvld.get();
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
				Object newReversed = ((ModelValueInstantiator<Object>) theReverse).forModelCopy(srcReversed, sourceModels,
					newModels);
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

	public static class SourceReplaceReverse<E extends ExElement> extends AbstractMapReverse<E> {
		private boolean isInexact;

		public SourceReplaceReverse(CompiledTransformation<?, ?, ?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType);
		}

		@Override
		public CompiledTransformation<?, ?, ?> getParentElement() {
			return super.getParentElement();
		}

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
				theReverse = getDefinition().getReverse().interpret(ModelTypes.Value.forType(sourceType), getExpressoEnv());
			}

			@Override
			public Instantiator<S, T> instantiate() {
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

	public static class SourceModifyingReverse<E extends ExElement> extends AbstractMapReverse<E> {
		public SourceModifyingReverse(CompiledTransformation<?, ?, ?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType);
		}

		@Override
		public <S, T> Interpreted<S, T, ? extends E> interpret(ExElement.Interpreted<?> parent) throws ExpressoInterpretationException {
			return new Interpreted<>(this, parent);
		}

		public static class Interpreted<S, T, E extends ExElement> extends AbstractMapReverse.Interpreted<S, T, E> {
			private InterpretedValueSynth<ObservableAction<?>, ObservableAction<?>> theReverse;

			public Interpreted(SourceModifyingReverse<? super E> definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public SourceModifyingReverse<? super E> getDefinition() {
				return (SourceModifyingReverse<? super E>) super.getDefinition();
			}

			@Override
			public InterpretedValueSynth<ObservableAction<?>, ObservableAction<?>> getReverse() {
				return theReverse;
			}

			@Override
			public void update(ModelComponentId sourceName, TypeToken<S> sourceType, TypeToken<T> targetType, InterpretedExpressoEnv env)
				throws ExpressoInterpretationException {
				super.update(sourceName, sourceType, targetType, env);
				theReverse = getDefinition().getReverse().interpret(ModelTypes.Action.any(), getExpressoEnv());
			}

			@Override
			public Instantiator<S, T> instantiate() {
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
				ModelValueInstantiator<ObservableAction<?>> reverse) {
				super(localModel, targetVariable, enabled, accept, add, addAccept, stateful, reverse);
			}

			@Override
			public ModelValueInstantiator<ObservableAction<?>> getReverse() {
				return (ModelValueInstantiator<ObservableAction<?>>) super.getReverse();
			}

			@Override
			protected TransformReverse<S, T> createReverse(ReverseParameters<S, T> parameters, ModelSetInstance models)
				throws ModelInstantiationException {
				models = getLocalModel().wrap(models);
				SettableValue<S> sourceV = parameters.sourceValue;
				SettableValue<T> targetV = parameters.targetValue;
				List<CombineWith.TransformationModification<S, T>> mods = parameters.modifications;

				ObservableAction<?> reversedEvld = getReverse().get(models);
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
}
