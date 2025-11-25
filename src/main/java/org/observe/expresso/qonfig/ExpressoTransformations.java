package org.observe.expresso.qonfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
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
import org.observe.XformOptions.XformDef;
import org.observe.assoc.ObservableMap;
import org.observe.assoc.ObservableMultiMap;
import org.observe.collect.ObservableCollection;
import org.observe.expresso.CompiledExpressoEnv;
import org.observe.expresso.ExpressoCompilationException;
import org.observe.expresso.ExpressoInterpretationException;
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
import org.observe.expresso.ops.Invocation;
import org.observe.expresso.ops.NameExpression;
import org.observe.expresso.qonfig.ExElement.Def;
import org.observe.util.TypeTokens;
import org.qommons.Identifiable;
import org.qommons.LambdaUtils;
import org.qommons.Named;
import org.qommons.Transaction;
import org.qommons.TriFunction;
import org.qommons.collect.BetterList;
import org.qommons.config.AbstractQIS;
import org.qommons.config.QonfigAddOn;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.config.QonfigInterpreterCore;

import com.google.common.reflect.TypeToken;

/** Contains general and abstract classes for transformations of model values to other model values */
public class ExpressoTransformations {
	/** The session key containing the {@link ModelType} being transformed */
	public static final String TARGET_MODEL_TYPE_KEY = "Expresso.Transformation.Target.Model.Type";

	private ExpressoTransformations() {
	}

	/**
	 * An abstract implementation for a &lt;transform> element, which produces a model value that uses another model value as a source and
	 * processes it through multiple dynamic transformations.
	 *
	 * @param <M1> The model type of the source value to transform
	 * @param <IM1> The internal souce model type
	 * @param <M2> The model type of the transformed value
	 * @param <IM2> The internal target model type
	 */
	@ExElementTraceable(toolkit = ExpressoBaseV0_1.BASE,
		qonfigType = "transform",
		interpretation = ExpressoTransformedElement.Interpreted.class)
	public static abstract class AbstractExpressoTransformedElement<M1, IM1, M2, IM2> extends ExElement.Def.Abstract<ModelValueElement<?>>
	implements ModelValueElement.CompiledSynth<M2, ModelValueElement<?>> {
		private String theModelPath;
		private final List<Operation<?, ?, ?>> theOperations;
		private ModelType<IM2> theTargetType;
		private boolean isPrepared;

		/**
		 * @param parent The parent element of this transform
		 * @param qonfigType The Qonfig type of this element
		 */
		protected AbstractExpressoTransformedElement(Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType);
			theOperations = new ArrayList<>();
		}

		@Override
		public String getModelPath() {
			return theModelPath;
		}

		/**
		 * @return The internal source model type
		 * @throws ExpressoCompilationException If the model type could not be determined
		 */
		protected abstract ModelType<IM1> getInternalSourceModelType() throws ExpressoCompilationException;

		/**
		 * @param env The expresso environment for expressions
		 * @return The internal target model type
		 * @throws ExpressoCompilationException If this transformation cannot be compiled
		 */
		protected ModelType<IM2> getInternalModelType(CompiledExpressoEnv env) throws ExpressoCompilationException {
			if (theOperations.isEmpty())
				return (ModelType<IM2>) getInternalSourceModelType();
			return (ModelType<IM2>) theOperations.get(theOperations.size() - 1).getTargetModelType();
		}

		/** @return The source value to transform */
		@QonfigAttributeGetter("source")
		public abstract CompiledExpression getSource();

		/** @return The transformation operations to process the source value into the transformed value */
		@QonfigChildGetter("op")
		public List<Operation<?, ?, ?>> getOperations() {
			return Collections.unmodifiableList(theOperations);
		}

		/** @return The internal target model type */
		protected ModelType<IM2> getInternalTargetType() {
			return theTargetType;
		}

		@Override
		public CompiledExpression getElementValue() {
			return null;
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			super.doUpdate(session);
			String path = session.get(ModelValueElement.PATH_KEY, String.class);
			if (path == null)
				path = "";
			if (path.length() > 0)
				path += ".";
			path += getAddOnValue(ExNamed.Def.class, ExNamed.Def::getName);
			theModelPath = path;
			isPrepared = false;
		}

		/**
		 * Fills this transformation from parsed Qonfig data
		 *
		 * @param session The session containing the data for this transformation
		 * @param sourceType The internal source model type
		 * @throws QonfigInterpretationException If this transformation could not be compiled
		 */
		protected void prepareModelValue(ExpressoQIS session, ModelType<IM1> sourceType) throws QonfigInterpretationException {
			if (isPrepared)
				return;
			isPrepared = true;
			ModelType<?> modelType = sourceType;
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
			theTargetType = (ModelType<IM2>) modelType;
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
			else if (modelType == ModelTypes.Map || modelType == ModelTypes.SortedMap)
				return (Class<? extends Operation<M, ?, ?>>) MapTransform.class;
			else if (modelType == ModelTypes.MultiMap || modelType == ModelTypes.SortedMultiMap)
				return (Class<? extends Operation<M, ?, ?>>) MultiMapTransform.class;
			else
				return null;
		}

		@Override
		public abstract Interpreted<M1, ?, IM1, ?, M2, ?, IM2, ?> interpretValue(ExElement.Interpreted<?> parent);

		/**
		 * Interpretation for {@link Transformation}
		 *
		 * @param <M1> The model type of the source value
		 * @param <MV1> The instance type of the source value
		 * @param <IM1> The internal source model type
		 * @param <IMV1> The internal source instance type
		 * @param <M2> The model type of the transformed value
		 * @param <MV2> The instance type of the transformed value
		 * @param <IM2> The internal target model type
		 * @param <IMV2> The internal target instance type
		 */
		public static abstract class Interpreted<M1, MV1 extends M1, IM1, IMV1 extends IM1, M2, MV2 extends M2, IM2, IMV2 extends IM2>
		extends ExElement.Interpreted.Abstract<ModelValueElement<MV2>>
		implements ModelValueElement.InterpretedSynth<M2, MV2, ModelValueElement<MV2>> {
			private final List<Operation.Interpreted<?, ?, ?, ?, ?>> theOperations;

			/**
			 * @param definition The definition of this transform
			 * @param parent The parent element of this transform
			 */
			protected Interpreted(AbstractExpressoTransformedElement<M1, IM1, M2, IM2> definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
				theOperations = new ArrayList<>();
			}

			@Override
			public AbstractExpressoTransformedElement<M1, IM1, M2, IM2> getDefinition() {
				return (AbstractExpressoTransformedElement<M1, IM1, M2, IM2>) super.getDefinition();
			}

			/** @return The source value to transform */
			public abstract InterpretedValueSynth<IM1, IMV1> getSource();

			/** @return The operations to perform on the source value to produce the transformed value */
			public List<Operation.Interpreted<?, ?, ?, ?, ?>> getOperations() {
				return Collections.unmodifiableList(theOperations);
			}

			@Override
			public InterpretedValueSynth<?, ?> getElementValue() {
				return null;
			}

			@Override
			public Interpreted<M1, MV1, IM1, IMV1, M2, MV2, IM2, IMV2> setParentElement(ExElement.Interpreted<?> parent) {
				super.setParentElement(parent);
				return this;
			}

			/**
			 * Updates this transformation
			 *
			 * @param sourceType The internal source instance type
			 * @throws ExpressoInterpretationException If this transformation cannot be interpreted
			 */
			protected void doUpdateValue(ModelInstanceType<IM1, IMV1> sourceType) throws ExpressoInterpretationException {
				ModelInstanceType<?, ?> type = sourceType;
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
					((Operation.Interpreted<Object, Object, ?, ?, ?>) interpOp).update((ModelInstanceType<Object, Object>) type);
					type = interpOp.getTargetType();
					i++;
				}
			}

			/** @return The internal target instance type */
			protected ModelInstanceType<IM2, IMV2> getInternalType() {
				return (ModelInstanceType<IM2, IMV2>) theOperations.get(theOperations.size() - 1).getTargetType();
			}

			@Override
			public ModelValueElement<MV2> create() {
				return null;
			}
		}

		static abstract class Instantiator<MV1, IMV1, MV2, IMV2> extends ModelValueElement.Abstract<MV2> {
			private final List<Operation.Instantiator<?, ?>> theOperations;
			private final boolean isEfficientCopy;
			private final TransformInstantiator<IMV1, IMV2> theFullTransform;
			private final String theAlias;

			protected Instantiator(AbstractExpressoTransformedElement.Interpreted<?, MV1, ?, IMV1, ?, MV2, ?, IMV2> interpreted)
				throws ModelInstantiationException {
				super(interpreted);
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
				theFullTransform = (TransformInstantiator<IMV1, IMV2>) fullTransform;
				theAlias = getModelPath() + ":" + interpreted.toString();
			}

			protected List<Operation.Instantiator<?, ?>> getOperations() {
				return Collections.unmodifiableList(theOperations);
			}

			public boolean isEfficientCopy() {
				return isEfficientCopy;
			}

			@Override
			public void instantiate() throws ModelInstantiationException {
				for (Operation.Instantiator<?, ?> op : theOperations)
					op.instantiate();
			}

			public IMV2 transformInternal(IMV1 source, ModelSetInstance models) throws ModelInstantiationException {
				IMV2 result = theFullTransform.transform(source, models);
				if (result instanceof Identifiable)
					((Identifiable) result).alias(theAlias);
				return result;
			}

			public boolean isDifferent(ModelSetInstance sourceModels, ModelSetInstance newModels) throws ModelInstantiationException {
				for (Operation.Instantiator<?, ?> op : theOperations)
					if (op.isDifferent(sourceModels, newModels))
						return true;
				return false;
			}
		}
	}

	/**
	 * A &lt;transform> element, which produces a model value that uses another model value as a source and processes it through multiple
	 * dynamic transformations.
	 *
	 * @param <M1> The model type of the source value to transform
	 * @param <M2> The model type of the transformed value
	 */
	@ExElementTraceable(toolkit = ExpressoBaseV0_1.BASE,
		qonfigType = "transform",
		interpretation = ExpressoTransformedElement.Interpreted.class)
	public static class ExpressoTransformedElement<M1, M2> extends AbstractExpressoTransformedElement<M1, M1, M2, M2> {
		private CompiledExpression theSource;

		/**
		 * @param parent The parent element of this transform
		 * @param qonfigType The Qonfig type of this element
		 */
		public ExpressoTransformedElement(Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType);
		}

		@Override
		public CompiledExpression getSource() {
			return theSource;
		}

		@Override
		protected ModelType<M1> getInternalSourceModelType() throws ExpressoCompilationException {
			return (ModelType<M1>) theSource.getModelType();
		}

		@Override
		public ModelType<M2> getModelType(CompiledExpressoEnv env) throws ExpressoCompilationException {
			return getInternalModelType(env);
		}

		@Override
		public CompiledExpression getElementValue() {
			return null;
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			super.doUpdate(session);
			theSource = getAttributeExpression("source", session);
		}

		@Override
		public void prepareModelValue(ExpressoQIS session) throws QonfigInterpretationException {
			ModelType<M1> modelType;
			try {
				modelType = (ModelType<M1>) theSource.getModelType();
			} catch (ExpressoCompilationException e) {
				throw new QonfigInterpretationException(e.getMessage(), e.getPosition(), e.getErrorLength(), e);
			}
			prepareModelValue(session, modelType);
		}

		@Override
		public Interpreted<M1, ?, M2, ?> interpretValue(ExElement.Interpreted<?> parent) {
			return new Interpreted<>(this, parent);
		}

		/**
		 * Interpretation for {@link Transformation}
		 *
		 * @param <M1> The model type of the source value
		 * @param <MV1> The instance type of the source value
		 * @param <M2> The model type of the transformed value
		 * @param <MV2> The instance type of the transformed value
		 */
		public static class Interpreted<M1, MV1 extends M1, M2, MV2 extends M2>
		extends AbstractExpressoTransformedElement.Interpreted<M1, MV1, M1, MV1, M2, MV2, M2, MV2> {
			private InterpretedValueSynth<M1, MV1> theSource;

			Interpreted(ExpressoTransformedElement<M1, M2> definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public ExpressoTransformedElement<M1, M2> getDefinition() {
				return (ExpressoTransformedElement<M1, M2>) super.getDefinition();
			}

			@Override
			public InterpretedValueSynth<M1, MV1> getSource() {
				return theSource;
			}

			@Override
			public ModelInstanceType<M2, MV2> getType() {
				return getInternalType();
			}

			@Override
			public void updateValue() throws ExpressoInterpretationException {
				update();
				try {
					theSource = interpret(getDefinition().getSource(),
						((ModelType<M1>) getDefinition().getSource().getModelType()).<MV1> anyAs());
				} catch (ExpressoCompilationException e) {
					throw new ExpressoInterpretationException(e.getMessage(), e.getPosition(), e.getErrorLength(), e);
				}
				doUpdateValue(theSource.getType());
			}

			@Override
			public List<? extends InterpretedValueSynth<?, ?>> getComponents() {
				return BetterList
					.of(Stream.concat(Stream.of(theSource), getOperations().stream().flatMap(op -> op.getComponents().stream())));
			}

			@Override
			public ModelValueElement<MV2> instantiate() throws ModelInstantiationException {
				return new Instantiator<>(this);
			}
		}

		static class Instantiator<MV1, MV2> extends AbstractExpressoTransformedElement.Instantiator<MV1, MV1, MV2, MV2> {
			private final ModelValueInstantiator<MV1> theSource;

			public Instantiator(ExpressoTransformedElement.Interpreted<?, MV1, ?, MV2> interpreted) throws ModelInstantiationException {
				super(interpreted);
				theSource = interpreted.getSource().instantiate();
			}

			@Override
			public void instantiate() throws ModelInstantiationException {
				super.instantiate();
				theSource.instantiate();
			}

			@Override
			protected MV2 evaluate(ModelSetInstance models) throws ModelInstantiationException, IllegalStateException {
				MV1 sourceValue = theSource.get(models);
				return transformInternal(sourceValue, models);
			}

			@Override
			public MV2 forModelCopy(MV2 value, ModelSetInstance sourceModels, ModelSetInstance newModels)
				throws ModelInstantiationException {
				if (isEfficientCopy()) {
					Object[] chain = new Object[getOperations().size() + 1];
					chain[getOperations().size()] = value;
					Object target = value;
					for (int i = getOperations().size() - 1; i >= 0; i--) {
						chain[i] = ((Operation.EfficientCopyingInstantiator<?, Object>) getOperations().get(i)).getSource(target);
						target = chain[i];
					}
					target = theSource.forModelCopy((MV1) chain[0], sourceModels, newModels);
					for (int i = 0; i < getOperations().size(); i++) {
						Object sourceValue = target;
						target = chain[i + 1];
						target = ((Operation.EfficientCopyingInstantiator<Object, Object>) getOperations().get(i)).forModelCopy(target,
							sourceValue, sourceModels, newModels);
					}

					return (MV2) target;
				} else {
					MV1 oldSource = theSource.get(sourceModels);
					MV1 newSource = theSource.forModelCopy(oldSource, sourceModels, newModels);
					boolean different = newSource != oldSource;
					for (Operation.Instantiator<?, ?> op : getOperations()) {
						if (different)
							break;
						if (op.isDifferent(sourceModels, newModels))
							different = true;
					}
					if (different)
						return transformInternal(newSource, newModels);
					else
						return value;
				}
			}
		}
	}

	/**
	 * A definition of a transformation operation. This compiled structure is capable of generating an interpreted structure
	 * ({@link Operation.Interpreted}) which can create an {@link Operation.Instantiator}, which is capable of transforming an actual model
	 * value into another (via {@link Operation.Instantiator#transform(Object, ModelSetInstance)}.
	 *
	 * @param <M1> The model type of the source value that this transformation accepts
	 * @param <M2> The model type of the target value that this transformation produces
	 * @param <E> The type of element that this definition produces
	 */
	public interface Operation<M1, M2, E extends ExElement> extends ExElement.Def<E> {
		/** @return The model type of the target value that this transformation produces */
		ModelType<? extends M2> getTargetModelType();

		/**
		 * Initializes or updates the operation
		 *
		 * @param session The session containing model and other data to use in initialization
		 * @param sourceModelType The source model type to operate upon
		 * @throws QonfigInterpretationException If this operation could not be initialized
		 */
		void update(ExpressoQIS session, ModelType<M1> sourceModelType) throws QonfigInterpretationException;

		@Override
		default void update(ExpressoQIS session) throws QonfigInterpretationException {
			throw new QonfigInterpretationException("Use update(ExpressionQIS, ModelType<M1>) instead",
				reporting().getFileLocation().getPosition(0), 0);
		}

		/**
		 * @param parent The parent element for the interpreted operation
		 * @return The interpreted operation
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
		 * @param <E> The type of expresso element produced
		 */
		public interface Interpreted<M1, MV1 extends M1, M2, MV2 extends M2, E extends ExElement> extends ExElement.Interpreted<E> {
			/**
			 * @param sourceType The type of the source values to transform
			 * @param env The expresso environment to interpret expressions with
			 * @throws ExpressoInterpretationException If the transformer could not be interpreted
			 */
			void update(ModelInstanceType<M1, MV1> sourceType) throws ExpressoInterpretationException;

			/** @return The type of the transformed observable */
			ModelInstanceType<? extends M2, ? extends MV2> getTargetType();

			/** @return Any model values used by this transformation */
			BetterList<InterpretedValueSynth<?, ?>> getComponents();

			/**
			 * @return The transformer capable of transforming source model values into transformed model values
			 * @throws ModelInstantiationException If the operation could not be instantiated
			 */
			Instantiator<MV1, MV2> instantiate() throws ModelInstantiationException;
		}

		/**
		 * Instantiator for an {@link Operation}, also a {@link TransformInstantiator}
		 *
		 * @param <MV1> The instance type of the source model value
		 * @param <MV2> The instance type of the transformed model value
		 */
		public interface Instantiator<MV1, MV2> extends TransformInstantiator<MV1, MV2> {
			/**
			 * Initializes model values in this instantiator. Must be called once after creation.
			 *
			 * @throws ModelInstantiationException If any model values could not be initialized
			 */
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

		/**
		 * An {@link Operation} {@link Instantiator} that may be able to make copies of transformed model values more efficiently
		 *
		 * @param <MV1> The instance type of the source model value
		 * @param <MV2> The instance type of the transformed model value
		 */
		public interface EfficientCopyingInstantiator<MV1, MV2> extends Instantiator<MV1, MV2> {
			/** @return Whether this instantiator is actually able to copy transformed values efficiently, given its configuration */
			boolean isEfficientCopy();

			/**
			 * @param value The transformed model value
			 * @return The source value that was transformed
			 */
			MV1 getSource(MV2 value);

			/**
			 * @param prevValue The transformed model value to copy
			 * @param newSource The new source value to transform
			 * @param sourceModels The source models that the copied values are from
			 * @param newModels The new models to produce the transformed value for
			 * @return The copied transformed value
			 * @throws ModelInstantiationException If the transformed value cannot be copied
			 */
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

		/**
		 * @param <S0> The source type of the other transformation
		 * @param before The transformation to combine with this one
		 * @return A transformation that applies the given <code>before</code> transformation to a source value, followed by this
		 *         transformation
		 */
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

			/**
			 * @param before The first transformation
			 * @param after The second transformation
			 */
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
	 * A transformer capable of transforming an {@link ObservableCollection} into another observable structure
	 *
	 * @param <M1> The model type of the source collection
	 * @param <M2> The model type of the target observable structure
	 * @param <E> The type of element produced
	 */
	public interface CollectionTransform<M1 extends ObservableCollection<?>, M2, E extends ExElement> extends Operation<M1, M2, E> {
	}

	/**
	 * A transformer capable of transforming an {@link ObservableMap} into another observable structure
	 *
	 * @param <M1> The model type of the source map
	 * @param <M2> The model type of the target observable structure
	 * @param <E> The type of element produced
	 */
	public interface MapTransform<M1 extends ObservableMap<?, ?>, M2, E extends ExElement> extends Operation<M1, M2, E> {
	}

	/**
	 * A transformer capable of transforming an {@link ObservableMultiMap} into another observable structure
	 *
	 * @param <M1> The model type of the source multi-map
	 * @param <M2> The model type of the target observable structure
	 * @param <E> The type of element produced
	 */
	public interface MultiMapTransform<M1 extends ObservableMultiMap<?, ?>, M2, E extends ExElement> extends Operation<M1, M2, E> {
	}

	/**
	 * Configures Qonfig interpretation for transformations
	 *
	 * @param interpreter The interpreter builder to configure
	 */
	public static void configureTransformation(QonfigInterpreterCore.Builder interpreter) {
		interpreter.createWith("transform", ExpressoTransformedElement.class, ExElement.creator(ExpressoTransformedElement::new));

		ObservableTransformations.configureTransformation(interpreter);

		ObservableActionTransformations.configureTransformation(interpreter);

		ObservableValueTransformations.configureTransformation(interpreter);

		ObservableCollectionTransformations.configureTransformation(interpreter);

		ObservableMapTransformations.configureTransformation(interpreter);

		ObservableMultiMapTransformations.configureTransformation(interpreter);

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

	/**
	 * Boilerplate-saving abstract class for transformation operations that produce the same type as the source value
	 *
	 * @param <M> The model type of the value
	 */
	public static abstract class TypePreservingTransform<M> extends ExElement.Def.Abstract<ExElement>
	implements Operation<M, M, ExElement> {
		private ModelType<M> theModelType;

		/**
		 * @param parent The parent element of the operation
		 * @param qonfigType The Qonfig type of this element
		 */
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

		/**
		 * @param parent The parent element for the interpreted operation
		 * @return The interpreted operation
		 */
		protected abstract Interpreted<M, ?> tppInterpret(ExElement.Interpreted<?> parent);

		/**
		 * Abstract interpretation for a {@link TypePreservingTransform}
		 *
		 * @param <M> The model type of the value
		 * @param <MV> The instance type of the value
		 */
		public static abstract class Interpreted<M, MV extends M> extends ExElement.Interpreted.Abstract<ExElement>
		implements Operation.Interpreted<M, MV, M, MV, ExElement> {
			private ModelInstanceType<M, MV> theType;

			/**
			 * @param definition The definition to interpret
			 * @param parent The parent element of this operation
			 */
			protected Interpreted(Def<? super ExElement> definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public void update(ModelInstanceType<M, MV> sourceType) throws ExpressoInterpretationException {
				theType = sourceType;
				super.update();
			}

			@Override
			public ModelInstanceType<? extends M, ? extends MV> getTargetType() {
				return theType;
			}
		}
	}

	/**
	 * Parses a filter expression. Filter expressions in expresso may return a boolean (whether an element is legal or not) or a String
	 * message (null if an element is legal, otherwise a message describing why it is not).
	 *
	 * @param testX The expression to parse
	 * @param element The element the expression belongs to (for environment)
	 * @param preferMessage Whether to first attempt to parse the expression as a message
	 * @return The message for the filter
	 * @throws ExpressoInterpretationException If the filter could not be parsed
	 */
	public static InterpretedValueSynth<SettableValue<?>, SettableValue<String>> parseFilter(CompiledExpression testX,
		ExElement.Interpreted<?> element, boolean preferMessage) throws ExpressoInterpretationException {
		if (testX == null)
			return null;
		InterpretedValueSynth<SettableValue<?>, SettableValue<String>> test;
		try {
			if (preferMessage)
				test = element.interpret(testX, ModelTypes.Value.forType(String.class));
			else {
				String uModMsg = testX.getFilePosition(0).toShortString()
					+ ": boolean->String filter not reversible";
				test = element.interpret(testX, ModelTypes.Value.forType(boolean.class))//
					.mapValue(ModelTypes.Value.forType(String.class),
						bv -> SettableValue.asSettable(bv.map(b -> b ? null : uModMsg), __ -> uModMsg));
			}
		} catch (ExpressoInterpretationException e) {
			try {
				if (preferMessage) {
					String uModMsg = testX.getFilePosition(0).toShortString()
						+ ": boolean->String filter not reversible";
					test = element.interpret(testX, ModelTypes.Value.forType(boolean.class))//
						.mapValue(ModelTypes.Value.forType(String.class),
							bv -> SettableValue.asSettable(bv.map(b -> b ? null : uModMsg), __ -> uModMsg));
				} else
					test = element.interpret(testX, ModelTypes.Value.forType(String.class));
			} catch (ExpressoInterpretationException e2) {
				throw new ExpressoInterpretationException("Could not interpret '" + testX + "' as a String or a boolean", e.getPosition(),
					e.getErrorLength(), e);
			}
		}
		return test;
	}

	/**
	 * A compiled structure capable of generating an {@link ExpressoTransformations.CompiledTransformation.Interpreted interpreted} one,
	 * which can create an {@link ExpressoTransformations.CompiledTransformation.Instantiator instantiated} one, which can create a
	 * {@link Transformation} that can transform model values using the ObServe {@link Transformation} API
	 *
	 * @param <M1> The model type of the source value
	 * @param <M2> The model type of the transformed value
	 * @param <E> The type of expresso element produced
	 */
	public interface CompiledTransformation<M1, M2, E extends ExElement> extends Operation<M1, M2, E> {
		/** @return The model ID of the variable that will contain the source value being transformed */
		ModelComponentId getSourceName();

		/** @return The model ID containing the previous result of the transformation, if it is currently available */
		ModelComponentId getPreviousResultAs();

		@Override
		Interpreted<M1, ?, ?, ?, M2, ?, ? extends E> interpret(ExElement.Interpreted<?> parent) throws ExpressoInterpretationException;

		/** @return Whether this transformation is reversible */
		boolean isReversible();

		/**
		 * An interpreted structure capable of creating an {@link ExpressoTransformations.CompiledTransformation.Instantiator instantiated}
		 * one, which can create a {@link Transformation} that can transform model values using the ObServe {@link Transformation} API
		 *
		 * @param <M1> The model type of the source value
		 * @param <S> The type of elements in the source value
		 * @param <T> The type of elements in the transformed value
		 * @param <MV1> The instance type of the source value
		 * @param <M2> The model type of the transformed value
		 * @param <MV2> The instance type of the transformed value
		 * @param <E> The type of expresso element produced
		 */
		public interface Interpreted<M1, S, T, MV1 extends M1, M2, MV2 extends M2, E extends ExElement>
		extends Operation.Interpreted<M1, MV1, M2, MV2, E> {
			@Override
			CompiledTransformation<M1, M2, ? super E> getDefinition();

			/** @return The type of value that this transformation produces */
			TypeToken<T> getTargetValueType();

			@Override
			Instantiator<S, T, MV1, MV2> instantiate() throws ModelInstantiationException;
		}

		/**
		 * An instantiated structure capable of generating a {@link Transformation} that can transform model values using the ObServe
		 * {@link Transformation} API
		 *
		 * @param <S> The type of elements in the source value
		 * @param <T> The type of elements in the transformed value
		 * @param <MV1> The instance type of the source value
		 * @param <MV2> The instance type of the transformed value
		 */
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
			 * @param precursor The transformation precursor to configure into a transformation
			 * @param models The model instances to use
			 * @return The observable transformation
			 * @throws ModelInstantiationException If the transformation could not be produced
			 */
			Transformation<S, T> transform(Transformation.ReversibleTransformationPrecursor<S, T, ?> precursor, ModelSetInstance models)
				throws ModelInstantiationException;
		}
	}

	/**
	 * A compiled structure defining how to modify a structure transformed with a {@link ExpressoTransformations.CompiledTransformation}
	 *
	 * @param <E> The type of expresso element produced
	 */
	public interface CompiledMapReverse<E extends ExElement> extends ExElement.Def<E> {
		/**
		 * @param <S> The type of the source values to produce
		 * @param <T> The type of the mapped values to reverse-transform
		 * @param parent The parent element for the interpreted map reverse
		 * @return The interpreted map reverse
		 * @throws ExpressoInterpretationException If the reverse transformation could not be produced
		 */
		<S, T> Interpreted<S, T, ? extends E> interpret(ExElement.Interpreted<?> parent) throws ExpressoInterpretationException;

		/**
		 * Interpretation of a {@link CompiledMapReverse}
		 *
		 * @param <S> The type of the source values to produce
		 * @param <T> The type of the mapped values to reverse-transform
		 * @param <E> The type of expresso element produced
		 */
		public interface Interpreted<S, T, E extends ExElement> extends ExElement.Interpreted<E> {
			/**
			 * @param sourceName The source model value
			 * @param sourceType The source type of the transformation
			 * @param targetType The target type of the transformation
			 * @param combinedTypes The names and types of all combined values incorporated into the transform
			 * @throws ExpressoInterpretationException If the reverse transformation could not be produced
			 */
			void update(ModelComponentId sourceName, TypeToken<S> sourceType, TypeToken<T> targetType)
				throws ExpressoInterpretationException;

			/** @return Any component model values that are used by this reverse */
			List<? extends InterpretedValueSynth<?, ?>> getComponents();

			/**
			 * @return The instantiated map reverse
			 * @throws ModelInstantiationException If the reverse could not be instantiated
			 */
			Instantiator<S, T> instantiate() throws ModelInstantiationException;
		}

		/**
		 * Instantiator for a {@link CompiledMapReverse}
		 *
		 * @param <S> The type of the source values to produce
		 * @param <T> The type of the mapped values to reverse-transform
		 */
		public interface Instantiator<S, T> {
			/**
			 * Instantiates model values in this map reverse. Must be called once after creation
			 *
			 * @throws ModelInstantiationException If any of this instantiator's components could not be instantiated
			 */
			void instantiate() throws ModelInstantiationException;

			/**
			 * @param sourceValue The source value being transformed
			 * @param previousResultValue The container to publish the previously-evaluated result value in
			 * @param targetType The transformed value type
			 * @param modifications Modifications from {@link ExpressoTransformations.CombineWith} operations to
			 *        {@link ExpressoTransformations.CombineWith.TransformationModification#prepareTransformOperation(TransformationValues)
			 *        prepare} when performing a reverse operation
			 * @param transformation The transformation to reverse
			 * @param models The model instance
			 * @return The transformation reverse
			 * @throws ModelInstantiationException If the reverse operation could not be instantiated
			 */
			Transformation.TransformReverse<S, T> reverse(SettableValue<S> sourceValue, SettableValue<T> previousResultValue,
				List<CombineWith.TransformationModification<S, T>> modifications, Transformation<S, T> transformation,
				ModelSetInstance models) throws ModelInstantiationException;

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
	}

	/**
	 * Abstract {@link ExpressoTransformations.CompiledTransformation} implementation
	 *
	 * @param <M1> The model type of the source value
	 * @param <M2> The model type of the transformed value
	 * @param <E> The type of expresso element produced
	 */
	@ExMultiElementTraceable({
		@ExElementTraceable(toolkit = ExpressoBaseV0_1.BASE,
			qonfigType = "map-to",
			interpretation = AbstractCompiledTransformation.Interpreted.class),
		@ExElementTraceable(toolkit = ExpressoBaseV0_1.BASE,
		qonfigType = "abst-map-op",
		interpretation = AbstractCompiledTransformation.Interpreted.class) })
	public static abstract class AbstractCompiledTransformation<M1, M2, E extends ExElement> extends ExElement.Def.Abstract<E>
	implements CompiledTransformation<M1, M2, E> {
		private ModelComponentId theSourceAs;
		private ModelComponentId thePreviousResultAs;
		private VariableType theType;
		private CompiledExpression theEquivalence;
		private final List<CombineWith<?>> theCombinedValues;
		private CompiledExpression theMap;
		private CompiledMapReverse<?> theReverse;

		private boolean isCached;
		private boolean isReEvalOnUpdate;
		private boolean isFireIfUnchanged;
		private boolean isNullToNull;
		private boolean isManyToOne;
		private boolean isOneToMany;

		/**
		 * @param parent The parent element of this transformation
		 * @param qonfigType The Qonfig type of this element
		 */
		protected AbstractCompiledTransformation(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType);
			theCombinedValues = new ArrayList<>();
		}

		@Override
		public ModelComponentId getSourceName() {
			return theSourceAs;
		}

		@QonfigAttributeGetter(asType = "map-to", value = "previous-result-as")
		@Override
		public ModelComponentId getPreviousResultAs() {
			return thePreviousResultAs;
		}

		/** @return The specified type of values produced by this transformation */
		public VariableType getType() {
			return theType;
		}

		/** @return See {@link XformDef#isCached()} */
		@QonfigAttributeGetter(asType = "abst-map-op", value = "cache")
		public boolean isCached() {
			return isCached;
		}

		/** @return See {@link XformDef#isReEvalOnUpdate()} */
		@QonfigAttributeGetter(asType = "abst-map-op", value = "re-eval-on-update")
		public boolean isReEvalOnUpdate() {
			return isReEvalOnUpdate;
		}

		/** @return See {@link XformDef#isFireIfUnchanged()} */
		@QonfigAttributeGetter(asType = "abst-map-op", value = "fire-if-unchanged")
		public boolean isFireIfUnchanged() {
			return isFireIfUnchanged;
		}

		/** @return See {@link XformDef#isNullToNull()} */
		@QonfigAttributeGetter(asType = "abst-map-op", value = "null-to-null")
		public boolean isNullToNull() {
			return isNullToNull;
		}

		/** @return See {@link XformDef#isManyToOne()} */
		@QonfigAttributeGetter(asType = "abst-map-op", value = "many-to-one")
		public boolean isManyToOne() {
			return isManyToOne;
		}

		/** @return See {@link XformDef#isOneToMany()} */
		@QonfigAttributeGetter(asType = "abst-map-op", value = "one-to-many")
		public boolean isOneToMany() {
			return isOneToMany;
		}

		/** @return The equivalence for the result value */
		@QonfigAttributeGetter(asType = "map-to", value = "equivalence")
		public CompiledExpression getEquivalence() {
			return theEquivalence;
		}

		/** @return &lt;combine-with> elements to combine with the source value to produce transformed values */
		@QonfigChildGetter(asType = "map-to", value = "combined-value")
		public List<CombineWith<?>> getCombinedValues() {
			return Collections.unmodifiableList(theCombinedValues);
		}

		/** @return The expression producing transformed values from source values */
		@QonfigAttributeGetter(asType = "map-to")
		public CompiledExpression getMap() {
			return theMap;
		}

		/** @return The &lt;map-reverse> element to allow modification of the transformed model value by operations on the source value */
		@QonfigChildGetter(asType = "map-to", value = "reverse")
		public CompiledMapReverse<?> getReverse() {
			return theReverse;
		}

		@Override
		public void update(ExpressoQIS session, ModelType<M1> sourceModelType) throws QonfigInterpretationException {
			super.update(session);
			// This is quicker than retrieving and grabbing it from the add-on
			String sourceAs = session.getAttributeText("source-as");
			String previousResultAs = session.getAttributeText("previous-result-as");
			ExWithElementModel.Def withElModel = getAddOn(ExWithElementModel.Def.class);
			theSourceAs = withElModel.getElementValueModelId(sourceAs);
			thePreviousResultAs = previousResultAs == null ? null : withElModel.getElementValueModelId(previousResultAs);
			theType = getAddOnValue(ExTyped.Def.class, ExTyped.Def::getValueType);
			isCached = session.getAttribute("cache", boolean.class);
			isReEvalOnUpdate = session.getAttribute("re-eval-on-update", boolean.class);
			isFireIfUnchanged = session.getAttribute("fire-if-unchanged", boolean.class);
			isNullToNull = session.getAttribute("null-to-null", boolean.class);
			isManyToOne = session.getAttribute("many-to-one", boolean.class);
			isOneToMany = session.getAttribute("one-to-many", boolean.class);

			theEquivalence = getAttributeExpression("equivalence", session);
			syncChildren(CombineWith.class, theCombinedValues, session.forChildren("combined-value"));
			theMap = getValueExpression(session);
			theReverse = syncChild(CompiledMapReverse.class, theReverse, session, "reverse");

			for (CombineWith<?> combine : theCombinedValues)
				withElModel.satisfyElementValueType(combine.getValueVariable(), ModelTypes.Value, //
					interp -> interp.interpret(combine.getElementValue(), ModelTypes.Value.any()).getType());
			withElModel.<Interpreted<M1, ?, ?, ?, M2, ?, E>, SettableValue<?>> satisfyElementValueType(theSourceAs, ModelTypes.Value, //
				interp -> ModelTypes.Value.forType(interp.getSourceType()));
			if (thePreviousResultAs != null) {
				if (refersToSource(theMap.getExpression(), thePreviousResultAs.getName())) {
					VariableType type = getAddOnValue(ExTyped.Def.class, ExTyped.Def::getValueType);
					if (type == null) {
						reporting().at(session.attributes().get("previous-result-as").getContent())
						.error("When the 'previous-result-as' variable is used to produce a value,"
							+ " the 'type' of the operation must be also to avoid circularity");
					}
				}
				withElModel.<Interpreted<M1, ?, ?, ?, M2, ?, E>, SettableValue<?>> satisfyElementValueType(thePreviousResultAs,
					ModelTypes.Value, interp -> ModelTypes.Value.forType(interp.getOrEvalTargetType()));
			}

		}

		@Override
		public boolean isReversible() {
			return theReverse != null;
		}

		/**
		 * Interpretation of an {@link AbstractCompiledTransformation}
		 *
		 * @param <M1> The model type of the source value
		 * @param <S> The type of values in the source model value
		 * @param <T> The type of values in the transformed model value
		 * @param <MV1> The instance type of the source value
		 * @param <M2> The model type of the transformed value
		 * @param <MV2> The instance type of the transformed value
		 * @param <E> The type of expresso element produced
		 */
		protected static abstract class Interpreted<M1, S, T, MV1 extends M1, M2, MV2 extends M2, E extends ExElement>
		extends ExElement.Interpreted.Abstract<E> implements CompiledTransformation.Interpreted<M1, S, T, MV1, M2, MV2, E> {
			private TypeToken<S> theSourceType;
			private InterpretedValueSynth<SettableValue<?>, SettableValue<Equivalence<? super T>>> theEquivalence;
			private final List<CombineWith.Interpreted<?, ?>> theCombinedValues;
			private InterpretedValueSynth<SettableValue<?>, SettableValue<T>> theMap;
			private CompiledMapReverse.Interpreted<S, T, ?> theReverse;
			private boolean isTesting;

			/**
			 * @param definition The definition to interpret
			 * @param parent The parent element for this transformation
			 */
			protected Interpreted(AbstractCompiledTransformation<M1, M2, ? super E> definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
				theCombinedValues = new ArrayList<>();
			}

			@Override
			public AbstractCompiledTransformation<M1, M2, ? super E> getDefinition() {
				return (AbstractCompiledTransformation<M1, M2, ? super E>) super.getDefinition();
			}

			/** @return The type of values in the source model value */
			public TypeToken<S> getSourceType() {
				return theSourceType;
			}

			/** @return The expression producing transformed values from source values */
			public InterpretedValueSynth<SettableValue<?>, SettableValue<T>> getMap() {
				return theMap;
			}

			/** @return The equivalence for the result value */
			public InterpretedValueSynth<SettableValue<?>, SettableValue<Equivalence<? super T>>> getEquivalence() {
				return theEquivalence;
			}

			/** @return &lt;combine-with> elements to combine with the source value to produce transformed values */
			public List<CombineWith.Interpreted<?, ?>> getCombinedValues() {
				return Collections.unmodifiableList(theCombinedValues);
			}

			TypeToken<T> getOrEvalTargetType() throws ExpressoInterpretationException {
				if (theMap == null) {
					TypeToken<T> evaluatedTT = (TypeToken<T>) interpretType(getDefinition().getType());
					if (evaluatedTT != null)
						return evaluatedTT;
					// ??? We need to do the combined values first, because the map-with may (and most likely does) use the combined value
					// variables
					syncChildren(getDefinition().getCombinedValues(), theCombinedValues, def -> def.interpret(this),
						i -> i.update());
					try (Transaction t = Invocation.asAction()) {
						theMap = interpret(getDefinition().getMap(), ModelTypes.Value.<SettableValue<T>> anyAs());
					}
				}
				return (TypeToken<T>) theMap.getType().getType(0);
			}

			/**
			 * @return The &lt;map-reverse> element to allow modification of the transformed model value by operations on the source value
			 */
			public CompiledMapReverse.Interpreted<S, T, ?> getReverse() {
				return theReverse;
			}

			/** @return Whether this transformation is being interpreted in a testing environment */
			public boolean isTesting() {
				return isTesting;
			}

			@Override
			public void update(ModelInstanceType<M1, MV1> sourceType) throws ExpressoInterpretationException {
				theMap = null;
				theSourceType = (TypeToken<S>) sourceType.getType(0);
				isTesting = getDefaultEnv().isTesting();
				super.update();
				TypeToken<T> targetType = getOrEvalTargetType();
				if (theMap == null)
					theMap = interpret(getDefinition().getMap(), ModelTypes.Value.forType(targetType));
				if (getDefinition().getEquivalence() == null)
					theEquivalence = null;
				else {
					theEquivalence = interpret(getDefinition().getEquivalence(),
						ModelTypes.Value.forType(TypeTokens.get().keyFor(Equivalence.class)
							.<Equivalence<? super T>> parameterized(TypeTokens.get().getSuperWildcard(targetType))));
				}
				theReverse = syncChild(getDefinition().getReverse(), theReverse, def -> def.interpret(this),
					r -> r.update(getDefinition().getSourceName(), theSourceType, targetType));
			}

			@Override
			public TypeToken<T> getTargetValueType() {
				return (TypeToken<T>) theMap.getType().getType(0);
			}

			@Override
			public BetterList<InterpretedValueSynth<?, ?>> getComponents() {
				List<InterpretedValueSynth<?, ?>> components = new ArrayList<>();
				components.add(theMap);
				if (theEquivalence != null)
					components.add(theEquivalence);
				if (theReverse != null)
					components.addAll(theReverse.getComponents());
				return BetterList.of(components);
			}
		}

		/**
		 * Instantiator for an {@link AbstractCompiledTransformation}
		 *
		 * @param <S> The type of values in the source model value
		 * @param <T> The type of values in the transformed model value
		 * @param <MV1> The instance type of the source value
		 * @param <MV2> The instance type of the transformed value
		 */
		public static abstract class Instantiator<S, T, MV1, MV2> implements CompiledTransformation.Instantiator<S, T, MV1, MV2> {
			private final DocumentMap<ModelInstantiator> theLocalModel;
			private final List<CombineWith.Instantiator<?>> theCombinedValues;
			private final ModelValueInstantiator<SettableValue<T>> theMap;
			private final S theDefaultSource;
			private final CompiledMapReverse.Instantiator<S, T> theReverse;
			private final ModelComponentId theSourceVariable;
			private final ModelComponentId thePreviousResultVariable;
			private final boolean isCached;
			private final boolean isReEvalOnUpdate;
			private final boolean isFireIfUnchanged;
			private final boolean isNullToNull;
			private final boolean isManyToOne;
			private final boolean isOneToMany;
			private final ModelValueInstantiator<SettableValue<Equivalence<? super T>>> theEquivalence;
			private final boolean isTesting;

			/**
			 * @param localModel The instantiator for the local model of the transformation
			 * @param map The instantiator for the map operation to produce transformed values from source values
			 * @param defaultSource Default value for the source variable
			 * @param combinedValues The instantiators for the &lt;combine-with> elements to combine with the source value to produce
			 *        transformed values
			 * @param reverse The instantiator for the &lt;map-reverse> element to allow modification of the transformed model value by
			 *        operations on the source value
			 * @param sourceVariable The model variable in which to publish the source value currently being transformed
			 * @param previousResultVariable The model ID of the value containing the previous result of the transformation when it is
			 *        available
			 * @param cached See {@link XformDef#isCached()}
			 * @param reEvalOnUpdate See {@link XformDef#isReEvalOnUpdate()}
			 * @param fireIfUnchanged See {@link XformDef#isFireIfUnchanged()}
			 * @param nullToNull See {@link XformDef#isNullToNull()}
			 * @param manyToOne See {@link XformDef#isManyToOne()}
			 * @param oneToMany See {@link XformDef#isOneToMany()}
			 * @param equivalence The equivalence for the transformed model value
			 * @param testing Whether this transformation is being instantiated in a testing environment
			 */
			protected Instantiator(DocumentMap<ModelInstantiator> localModel, ModelValueInstantiator<SettableValue<T>> map, S defaultSource,
				List<CombineWith.Instantiator<?>> combinedValues, CompiledMapReverse.Instantiator<S, T> reverse,
				ModelComponentId sourceVariable, ModelComponentId previousResultVariable, boolean cached, boolean reEvalOnUpdate,
				boolean fireIfUnchanged, boolean nullToNull,
				boolean manyToOne, boolean oneToMany, ModelValueInstantiator<SettableValue<Equivalence<? super T>>> equivalence,
				boolean testing) {
				theLocalModel = localModel;
				theMap = map;
				theDefaultSource = defaultSource;
				theCombinedValues = combinedValues;
				theReverse = reverse;
				theSourceVariable = sourceVariable;
				thePreviousResultVariable = previousResultVariable;
				isCached = cached;
				isReEvalOnUpdate = reEvalOnUpdate;
				isFireIfUnchanged = fireIfUnchanged;
				isNullToNull = nullToNull;
				isManyToOne = manyToOne;
				isOneToMany = oneToMany;
				theEquivalence = equivalence;
				isTesting = testing;
			}

			/** @return Whether this transformation is reversible */
			protected boolean isReversible() {
				return theReverse != null;
			}

			@Override
			public void instantiate() throws ModelInstantiationException {
				theLocalModel.forEach(ModelInstantiator::instantiate);
				theMap.instantiate();
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
				models = theLocalModel.operate(models, (m, mi) -> mi.wrap(m));
				SettableValue<S> sourceV = SettableValue.<S> build()//
					.withValue(theDefaultSource)//
					.build();
				SettableValue<T> previousResultV = thePreviousResultVariable == null ? null : SettableValue.create();
				ExFlexibleElementModelAddOn.satisfyElementValue(theSourceVariable, models, sourceV);
				if (previousResultV != null)
					ExFlexibleElementModelAddOn.satisfyElementValue(thePreviousResultVariable, models, previousResultV);
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

				SettableValue<T> targetV = theMap.get(models);
				MaybeReversibleTransformation<S, T> transformation = buildTransformation(builder, sourceV, previousResultV, targetV,
					modifications, models);

				Transformation<S, T> finalTransformation;
				if (theReverse != null)
					finalTransformation = transformation.withReverse(//
						theReverse.reverse(sourceV, previousResultV, modifications, transformation, models));
				else
					finalTransformation = transformation.withReverse(//
						reverse(sourceV, previousResultV, targetV, modifications, transformation, models));

				if (theEquivalence != null) {
					Equivalence<? super T> equivalence = theEquivalence.get(models).get();
					finalTransformation = finalTransformation.withEquivalence(equivalence);
				}
				return finalTransformation;
			}

			@Override
			public boolean isDifferent(ModelSetInstance sourceModels, ModelSetInstance newModels) throws ModelInstantiationException {
				if (theLocalModel != null) {
					sourceModels = theLocalModel.operate(sourceModels, (m, mi) -> mi.wrap(m));
					newModels = theLocalModel.operate(newModels, (m, mi) -> mi.wrap(m));
				}
				SettableValue<T> map = theMap.get(sourceModels);
				if (map != theMap.forModelCopy(map, sourceModels, newModels))
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

			/**
			 * Creates a transformation
			 *
			 * @param builder The transformation builder
			 * @param sourceValue The container for the source value to transform
			 * @param previousResultValue The value containing the previous result of the transformation when it is available
			 * @param mappedValue The container for the transformed value
			 * @param modifications Modifications from {@link ExpressoTransformations.CombineWith}s to
			 *        {@link ExpressoTransformations.CombineWith.TransformationModification#prepareTransformOperation(TransformationValues)
			 *        prepare} before tranformation operations
			 * @param models The model instance to get model values from
			 * @return The transformation
			 * @throws ModelInstantiationException If the transformation could not be configured
			 */
			public MaybeReversibleTransformation<S, T> buildTransformation(ReversibleTransformationBuilder<S, T, ?> builder,
				SettableValue<S> sourceValue, SettableValue<T> previousResultValue, SettableValue<T> mappedValue,
				List<CombineWith.TransformationModification<S, T>> modifications, ModelSetInstance models)
					throws ModelInstantiationException {
				BiFunction<S, Transformation.TransformationValues<? extends S, ? extends T>, T> mapFn = LambdaUtils
					.printableBiFn((source, tvs) -> {
						sourceValue.set(source, null);
						if (previousResultValue != null)
							previousResultValue.set(tvs.getPreviousResult());
						for (CombineWith.TransformationModification<S, T> mod : modifications)
							mod.prepareTransformOperation(tvs);
						return mappedValue.get();
					}, theMap::toString, null);
				return builder.build(mapFn).withTesting(isTesting);
			}

			/**
			 * Produces a default reverse for the transformation if no &lt;map-reverse> is specified
			 *
			 * @param sourceV The container for the source value to reverse
			 * @param previousResultValue The value containing the previous result of the transformation when it is available
			 * @param mappedValue The container for the transformed value to reverse
			 * @param modifications Modifications from {@link ExpressoTransformations.CombineWith}s to
			 *        {@link ExpressoTransformations.CombineWith.TransformationModification#prepareTransformOperation(TransformationValues)
			 *        prepare} before reverse operations
			 * @param transformation The transformation operation to reverse
			 * @param models The model instance to get model values from
			 * @return The reverse operation
			 * @throws ModelInstantiationException If the reverse operation could not be configured
			 */
			public TransformReverse<S, T> reverse(SettableValue<S> sourceV, SettableValue<T> previousResultValue,
				SettableValue<T> mappedValue,
				List<CombineWith.TransformationModification<S, T>> modifications, MaybeReversibleTransformation<S, T> transformation,
				ModelSetInstance models) throws ModelInstantiationException {
				BiFunction<T, Transformation.TransformationValues<? extends S, ? extends T>, S> reverseFn;
				Function<Transformation.TransformationValues<? extends S, ? extends T>, String> enabledFn;
				BiFunction<T, Transformation.TransformationValues<? extends S, ? extends T>, String> acceptFn;
				TriFunction<T, Transformation.TransformationValues<? extends S, ? extends T>, Boolean, S> addFn;
				BiFunction<T, Transformation.TransformationValues<? extends S, ? extends T>, String> addAcceptFn;
				reverseFn = (target, tvs) -> {
					prepareTx(tvs, modifications);
					if (previousResultValue != null)
						previousResultValue.set(tvs.getPreviousResult());
					mappedValue.set(target, null);
					return sourceV.get();
				};
				enabledFn = tvs -> {
					prepareTx(tvs, modifications);
					if (previousResultValue != null)
						previousResultValue.set(tvs.getPreviousResult());
					return mappedValue.isEnabled().get();
				};
				acceptFn = (target, tvs) -> {
					prepareTx(tvs, modifications);
					if (previousResultValue != null)
						previousResultValue.set(tvs.getPreviousResult());
					return mappedValue.isAcceptable(target);
				};
				addFn = (target, tvs, test) -> {
					if (previousResultValue != null)
						previousResultValue.set(null);
					return reverseFn.apply(target, tvs);
				};
				addAcceptFn = acceptFn;
				return new Transformation.SourceReplacingReverse<>(transformation, reverseFn, enabledFn, acceptFn, addFn, addAcceptFn,
					false, false);
			}

			/**
			 * A utility function to populate needed model values for each transformation operation. Must be static because this is invoked
			 * from the evaluated phase.
			 *
			 * @param <S> The type of the source values
			 * @param <T> The type of the transformed values
			 * @param tvs The transformation values of the operation
			 * @param modifications The {@link ExpressoTransformations.CombineWith} modifications to
			 *        {@link ExpressoTransformations.CombineWith.TransformationModification#prepareTransformOperation(TransformationValues)
			 *        prepare}
			 */
			protected static <S, T> void prepareTx(Transformation.TransformationValues<? extends S, ? extends T> tvs,
				List<CombineWith.TransformationModification<S, T>> modifications) {
				for (CombineWith.TransformationModification<S, T> mod : modifications)
					mod.prepareTransformOperation(tvs);
			}
		}

		/**
		 * Supports {@link Operation.EfficientCopyingInstantiator} for &lt;map-to> operations
		 *
		 * @param <S> The source type of the transformation
		 * @param <T> The target type of the transformation
		 * @param <MV1> The instance type of the source value
		 * @param <MV2> The instance type of the transformed value
		 */
		public static abstract class EfficientCopyingInstantiator<S, T, MV1, MV2> extends Instantiator<S, T, MV1, MV2>
		implements Operation.EfficientCopyingInstantiator<MV1, MV2> {
			/**
			 * @param localModel The instantiator for the local model of the transformation
			 * @param map The instantiator for the &lt;map-with> operator to produce transformed values from source values
			 * @param defaultSource Default value for the source variable
			 * @param combinedValues The instantiators for the &lt;combine-with> elements to combine with the source value to produce
			 *        transformed values
			 * @param reverse The instantiator for the &lt;map-reverse> element to allow modification of the transformed model value by
			 *        operations on the source value
			 * @param sourceVariable The model variable in which to publish the source value currently being transformed
			 * @param previousResultVariable The model ID of the value containing the previous result of the transformation when it is
			 *        available
			 * @param cached See {@link XformDef#isCached()}
			 * @param reEvalOnUpdate See {@link XformDef#isReEvalOnUpdate()}
			 * @param fireIfUnchanged See {@link XformDef#isFireIfUnchanged()}
			 * @param nullToNull See {@link XformDef#isNullToNull()}
			 * @param manyToOne See {@link XformDef#isManyToOne()}
			 * @param oneToMany See {@link XformDef#isOneToMany()}
			 * @param equivalence The equivalence for the transformed model value
			 * @param testing Whether this transformation is being instantiated in a testing environment
			 */
			protected EfficientCopyingInstantiator(DocumentMap<ModelInstantiator> localModel, ModelValueInstantiator<SettableValue<T>> map,
				S defaultSource, List<CombineWith.Instantiator<?>> combinedValues, CompiledMapReverse.Instantiator<S, T> reverse,
				ModelComponentId sourceVariable, ModelComponentId previousResultVariable, boolean cached, boolean reEvalOnUpdate,
				boolean fireIfUnchanged, boolean nullToNull,
				boolean manyToOne, boolean oneToMany, ModelValueInstantiator<SettableValue<Equivalence<? super T>>> equivalence,
				boolean testing) {
				super(localModel, map, defaultSource, combinedValues, reverse, sourceVariable, previousResultVariable, cached,
					reEvalOnUpdate, fireIfUnchanged,
					nullToNull, manyToOne, oneToMany, equivalence, testing);
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

	/**
	 * A &lt;combine-with> element in a &lt;map-to> operation defining another model value to combine with the source value into the
	 * transformed value
	 *
	 * @param <E> The type of expresso element produced
	 */
	@ExElementTraceable(toolkit = ExpressoBaseV0_1.BASE, qonfigType = "combine-with", interpretation = CombineWith.Interpreted.class)
	public static class CombineWith<E extends ExElement> extends ExElement.Def.Abstract<E> implements Named {
		private CompiledExpression theValue;
		private ModelComponentId theValueVariable;

		/**
		 * @param parent The &lt;map-to> parent for this combine-with
		 * @param qonfigType The Qonfig type of this element
		 */
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

		/** @return The model variable in which this combined value will be available to expressions */
		public ModelComponentId getValueVariable() {
			return theValueVariable;
		}

		/** @return The value to combine */
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

		/**
		 * @param parent The parent element for the interpreted combine-with
		 * @return The interpreted combine-with
		 */
		public Interpreted<?, ? extends E> interpret(AbstractCompiledTransformation.Interpreted<?, ?, ?, ?, ?, ?, ?> parent) {
			return new Interpreted<>(this, parent);
		}

		/**
		 * Interpretation for {@link ExpressoTransformations.CombineWith}
		 *
		 * @param <T> The type of the combined value
		 * @param <E> The type of expresso element produced
		 */
		protected static class Interpreted<T, E extends ExElement> extends ExElement.Interpreted.Abstract<E> {
			private InterpretedValueSynth<SettableValue<?>, SettableValue<T>> theValue;

			Interpreted(CombineWith<? super E> definition, AbstractCompiledTransformation.Interpreted<?, ?, ?, ?, ?, ?, ?> parent) {
				super(definition, parent);
			}

			@Override
			public CombineWith<? super E> getDefinition() {
				return (CombineWith<? super E>) super.getDefinition();
			}

			void getValue() throws ExpressoInterpretationException {
				theValue = interpret(getDefinition().getElementValue(), ModelTypes.Value.<SettableValue<T>> anyAs());
			}

			@Override
			public AbstractCompiledTransformation.Interpreted<?, ?, ?, ?, ?, ?, ?> getParentElement() {
				return (AbstractCompiledTransformation.Interpreted<?, ?, ?, ?, ?, ?, ?>) super.getParentElement();
			}

			/** @return The value to combine */
			public InterpretedValueSynth<SettableValue<?>, SettableValue<T>> getElementValue() {
				return theValue;
			}

			@Override
			protected void doUpdate() throws ExpressoInterpretationException {
				super.doUpdate();
				getValue();
			}

			/**
			 * @return The combine-with instantiator
			 * @throws ModelInstantiationException If the combine-with could not be instantiated
			 */
			public Instantiator<T> instantiate() throws ModelInstantiationException {
				return new Instantiator<>(theValue.instantiate(), getDefinition().getValueVariable());
			}
		}

		/**
		 * Instantiator for a {@link ExpressoTransformations.CombineWith}
		 *
		 * @param <T> The type of the combined value
		 */
		public static class Instantiator<T> {
			private final ModelValueInstantiator<SettableValue<T>> theValue;
			private final ModelComponentId theValueVariable;

			Instantiator(ModelValueInstantiator<SettableValue<T>> value, ModelComponentId valueVariable) {
				theValue = value;
				theValueVariable = valueVariable;
			}

			void instantiate() throws ModelInstantiationException {
				theValue.instantiate();
			}

			/**
			 * Adds this combined value into the transformation
			 *
			 * @param <S> The type of the source values to transform
			 * @param <T2> The type of the transformed value
			 * @param builder The builder to configure
			 * @param models The model instance to get model values from
			 * @param modify Accepts {@link TransformationModification modification}s to be
			 *        {@link TransformationModification#prepareTransformOperation(TransformationValues) prepared} before each transformation
			 *        operation
			 * @return The configured transformation
			 * @throws ModelInstantiationException If the transformation could not be configured
			 */
			public <S, T2> ReversibleTransformationBuilder<S, T2, ?> addTo(ReversibleTransformationBuilder<S, T2, ?> builder,
				ModelSetInstance models, Consumer<? super TransformationModification<S, T2>> modify) throws ModelInstantiationException {
				SettableValue<T> sourceV = theValue.get(models);
				SettableValue<T> targetV = SettableValue.<T> build().build();
				ExFlexibleElementModelAddOn.satisfyElementValue(theValueVariable, models,
					targetV.disableWith(SettableValue.ALWAYS_DISABLED));
				modify.accept(new DefaultTransformationModification<>(sourceV, targetV));
				return builder.combineWith(sourceV);
			}

			/**
			 * @param sourceModels The source models to compare
			 * @param newModels The new models to compare
			 * @return Whether this operation's instantiation differs from one model instance set to the next
			 * @throws ModelInstantiationException If the differences could not be determined
			 */
			public boolean isDifferent(ModelSetInstance sourceModels, ModelSetInstance newModels) throws ModelInstantiationException {
				SettableValue<T> sourceV = theValue.get(sourceModels);
				return sourceV != theValue.forModelCopy(sourceV, sourceModels, newModels);
			}
		}

		/**
		 * A hook into a transformation operation to be notified before the transformed or reversed value is obtained. Allows
		 * {@link ExpressoTransformations.CombineWith} instantiators to update their current value in the model environment.
		 *
		 * @param <S> The type of the source value
		 * @param <T> The type of the transformed value
		 */
		public interface TransformationModification<S, T> {
			/** @param txValues The transformation values containing the combined value to publish */
			void prepareTransformOperation(Transformation.TransformationValues<? extends S, ? extends T> txValues);
		}

		/**
		 * Default {@link TransformationModification} implementation
		 *
		 * @param <T> The type of the combined value
		 * @param <S> The type of the source value
		 * @param <T2> The type of the transformed value
		 */
		public static class DefaultTransformationModification<T, S, T2> implements TransformationModification<S, T2> {
			private final SettableValue<T> theSourceValue;
			private final SettableValue<T> theTargetValue;

			/**
			 * @param sourceValue The combined value
			 * @param targetValue The container to publish the combined value into
			 */
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

	/**
	 * Abstract {@link CompiledMapReverse} implementation
	 *
	 * @param <E> The type of expresso element produced
	 */
	@ExMultiElementTraceable({
		@ExElementTraceable(toolkit = ExpressoBaseV0_1.BASE,
			qonfigType = "map-reverse",
			interpretation = AbstractMapReverse.Interpreted.class),
		@ExElementTraceable(toolkit = ExpressoBaseV0_1.BASE,
		qonfigType = "abst-map-reverse",
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

		/**
		 * @param parent The &lt;map-to> parent of this reverse
		 * @param qonfigType The Qonfig type of this element
		 */
		protected AbstractMapReverse(CompiledTransformation<?, ?, ?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType);
		}

		@Override
		public CompiledTransformation<?, ?, ?> getParentElement() {
			return (CompiledTransformation<?, ?, ?>) super.getParentElement();
		}

		/** @return The type of the transformation reverse */
		@QonfigAttributeGetter(asType = "map-reverse", value = "type")
		public QonfigAddOn getType() {
			return theType;
		}

		/** @return The model variable containing the target value to use to modify or replace the source value */
		@QonfigAttributeGetter(asType = "map-reverse", value = "target-as")
		public ModelComponentId getTargetVariable() {
			return theTargetVariable;
		}

		/** @return The expression to do the work of the reverse operation */
		@QonfigAttributeGetter(asType = "map-reverse")
		public CompiledExpression getReverse() {
			return theReverse;
		}

		/** @return Whether and when the reverse operation is enabled */
		@QonfigAttributeGetter(asType = "abst-map-reverse", value = "enabled")
		public CompiledExpression getEnabled() {
			return theEnabled;
		}

		/** @return Whether the reverse operation is enabled for a given target value */
		@QonfigAttributeGetter(asType = "abst-map-reverse", value = "accept")
		public CompiledExpression getAccept() {
			return theAccept;
		}

		/** @return Whether this reverse operation can add values to a collection */
		@QonfigAttributeGetter(asType = "abst-map-reverse", value = "add")
		public CompiledExpression getAdd() {
			return theAdd;
		}

		/** @return Whether this reverse operation can add a particular value to a collection */
		@QonfigAttributeGetter(asType = "abst-map-reverse", value = "add-accept")
		public CompiledExpression getAddAccept() {
			return theAddAccept;
		}

		/** @return Whether this reverse operation depends on the current source value */
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

			boolean stateful = refersToSource(theReverse.getExpression(), getParentElement().getSourceName().getName());
			if (!stateful && getParentElement().getPreviousResultAs() != null)
				stateful = refersToSource(theReverse.getExpression(), getParentElement().getPreviousResultAs().getName());
			if (!stateful && theEnabled != null) {
				stateful = refersToSource(theEnabled.getExpression(), getParentElement().getSourceName().getName());
				if (!stateful && getParentElement().getPreviousResultAs() != null)
					stateful = refersToSource(theEnabled.getExpression(), getParentElement().getPreviousResultAs().getName());
			}
			if (!stateful && theAccept != null) {
				stateful = refersToSource(theAccept.getExpression(), getParentElement().getSourceName().getName());
				if (!stateful && getParentElement().getPreviousResultAs() != null)
					stateful = refersToSource(theAccept.getExpression(), getParentElement().getPreviousResultAs().getName());
			}
			if (!stateful && theAdd != null) {
				stateful = refersToSource(theAdd.getExpression(), getParentElement().getSourceName().getName());
				if (!stateful && getParentElement().getPreviousResultAs() != null)
					stateful = refersToSource(theAdd.getExpression(), getParentElement().getPreviousResultAs().getName());
			}
			isStateful = stateful;

			getAddOn(ExWithElementModel.Def.class).<Interpreted<?, ?, E>, SettableValue<?>> satisfyElementSingleValueType(theTargetVariable,
				ModelTypes.Value, Interpreted::getTargetType);
		}

		/**
		 * Interpretation of an {@link AbstractMapReverse}
		 *
		 * @param <S> The source value type
		 * @param <T> The transformed value type
		 * @param <E> The type of expresso element produced
		 */
		public static abstract class Interpreted<S, T, E extends ExElement> extends ExElement.Interpreted.Abstract<E>
		implements CompiledMapReverse.Interpreted<S, T, E> {
			private TypeToken<S> theSourceType;
			private TypeToken<T> theTargetType;
			private InterpretedValueSynth<SettableValue<?>, SettableValue<String>> theEnabled;
			private InterpretedValueSynth<SettableValue<?>, SettableValue<String>> theAccept;
			private InterpretedValueSynth<SettableValue<?>, SettableValue<S>> theAdd;
			private InterpretedValueSynth<SettableValue<?>, SettableValue<String>> theAddAccept;

			/**
			 * @param definition The definition to interpret
			 * @param parent The parent element for this interpreted map reverse
			 */
			protected Interpreted(AbstractMapReverse<? super E> definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public AbstractMapReverse<? super E> getDefinition() {
				return (AbstractMapReverse<? super E>) super.getDefinition();
			}

			/** @return The type of the source values */
			public TypeToken<S> getSourceType() {
				return theSourceType;
			}

			/** @return The type of the transformed values */
			public TypeToken<T> getTargetType() {
				return theTargetType;
			}

			/** @return The expression to do the work of the reverse operation */
			public abstract InterpretedValueSynth<?, ?> getReverse();

			/** @return Whether and when the reverse operation is enabled */
			public InterpretedValueSynth<SettableValue<?>, SettableValue<String>> getEnabled() {
				return theEnabled;
			}

			/** @return Whether the reverse operation is enabled for a given target value */
			public InterpretedValueSynth<SettableValue<?>, SettableValue<String>> getAccept() {
				return theAccept;
			}

			/** @return Whether this reverse operation can add values to a collection */
			public InterpretedValueSynth<SettableValue<?>, SettableValue<S>> getAdd() {
				return theAdd;
			}

			/** @return Whether this reverse operation can add a particular value to a collection */
			public InterpretedValueSynth<SettableValue<?>, SettableValue<String>> getAddAccept() {
				return theAddAccept;
			}

			@Override
			public void update(ModelComponentId sourceName, TypeToken<S> sourceType, TypeToken<T> targetType)
				throws ExpressoInterpretationException {
				theSourceType = sourceType;
				theTargetType = targetType;
				super.update();
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

		/**
		 * Instantiator for an {@link AbstractMapReverse}
		 *
		 * @param <S> The source value type
		 * @param <T> The transformed value type
		 */
		public static abstract class Instantiator<S, T> implements CompiledMapReverse.Instantiator<S, T> {
			private final DocumentMap<ModelInstantiator> theLocalModel;
			private final ModelComponentId theTargetVariable;
			private final ModelValueInstantiator<SettableValue<String>> theEnabled;
			private final ModelValueInstantiator<SettableValue<String>> theAccept;
			private final ModelValueInstantiator<SettableValue<S>> theAdd;
			private final ModelValueInstantiator<SettableValue<String>> theAddAccept;
			private final boolean isStateful;
			private final ModelValueInstantiator<?> theReverse;

			/**
			 * @param localModel The local model for the reverse element
			 * @param targetVariable The model variable containing the target value to use to modify or replace the source value
			 * @param enabled Whether and when the reverse operation is enabled
			 * @param accept Whether the reverse operation is enabled for a given target value
			 * @param add Whether this reverse operation can add values to a collection
			 * @param addAccept Whether this reverse operation can add a particular value to a collection
			 * @param stateful Whether this reverse operation depends on the current source value
			 * @param reverse The expression to do the work of the reverse operation
			 */
			protected Instantiator(DocumentMap<ModelInstantiator> localModel, ModelComponentId targetVariable,
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

			/** @return The local model for the reverse element */
			public DocumentMap<ModelInstantiator> getLocalModel() {
				return theLocalModel;
			}

			/** @return The model variable containing the target value to use to modify or replace the source value */
			public ModelComponentId getTargetVariable() {
				return theTargetVariable;
			}

			/** @return Whether and when the reverse operation is enabled */
			public ModelValueInstantiator<SettableValue<String>> getEnabled() {
				return theEnabled;
			}

			/** @return Whether the reverse operation is enabled for a given target value */
			public ModelValueInstantiator<SettableValue<String>> getAccept() {
				return theAccept;
			}

			/** @return Whether this reverse operation can add values to a collection */
			public ModelValueInstantiator<SettableValue<S>> getAdd() {
				return theAdd;
			}

			/** @return Whether this reverse operation can add a particular value to a collection */
			public ModelValueInstantiator<SettableValue<String>> getAddAccept() {
				return theAddAccept;
			}

			/** @return Whether this reverse operation depends on the current source value */
			public boolean isStateful() {
				return isStateful;
			}

			/** @return The expression to do the work of the reverse operation */
			public ModelValueInstantiator<?> getReverse() {
				return theReverse;
			}

			@Override
			public void instantiate() throws ModelInstantiationException {
				theLocalModel.forEach(ModelInstantiator::instantiate);
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

			/**
			 * @param models The models to wrap
			 * @return The given models, wrapped with this map-reverse's internal models, if any
			 * @throws ModelInstantiationException If this map-reverse's internal models couldn't be instantiated
			 */
			protected ModelSetInstance wrapModels(ModelSetInstance models) throws ModelInstantiationException {
				return theLocalModel.operate(models, (m, mi) -> mi.wrap(m));
			}

			@Override
			public TransformReverse<S, T> reverse(SettableValue<S> sourceV, SettableValue<T> previousResultV,
				List<CombineWith.TransformationModification<S, T>> modifications, Transformation<S, T> transformation,
				ModelSetInstance models) throws ModelInstantiationException {
				models = wrapModels(models);

				SettableValue<T> targetV = SettableValue.<T> build().build();
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
					prepareTx(tvs, isStateful, sourceV, previousResultV, targetV, null, modifications);
					return enabledEvld.get();
				}, enabledEvld::toString, enabledEvld);
				acceptFn = acceptEvld == null ? null : LambdaUtils.printableBiFn((target, tvs) -> {
					prepareTx(tvs, isStateful, sourceV, previousResultV, targetV, target, modifications);
					return acceptEvld.get();
				}, acceptEvld::toString, acceptEvld);
				addFn = addEvld == null ? null : LambdaUtils.printableTriFn((target, tvs, test) -> {
					prepareTx(tvs, isStateful, sourceV, previousResultV, targetV, target, modifications);
					return addEvld.get();
				}, addEvld::toString, addEvld);
				addAcceptFn = addAcceptEvld == null ? null : LambdaUtils.printableBiFn((target, tvs) -> {
					prepareTx(tvs, isStateful, sourceV, previousResultV, targetV, target, modifications);
					return addAcceptEvld.get();
				}, addAcceptEvld::toString, addAcceptEvld);
				return createReverse(new ReverseParameters<>(sourceV, previousResultV, targetV, modifications, transformation, enabledFn,
					acceptFn, addFn, addAcceptFn, isStateful), models);
			}

			/**
			 * @param parameters The parameter structure containing all the model expressions known to this abstract type
			 * @param models The model instance
			 * @return The reverse transformation
			 * @throws ModelInstantiationException If the reverse could not be created
			 */
			protected abstract TransformReverse<S, T> createReverse(ReverseParameters<S, T> parameters, ModelSetInstance models)
				throws ModelInstantiationException;

			/**
			 * A utility function to populate needed model values for each reverse operation. Must be static because this is invoked from
			 * the evaluated phase.
			 *
			 * @param <S> The source value type
			 * @param <T> The transformed value type
			 * @param tvs The transformation values to prepare the transformation with
			 * @param stateful Whether the reverse operation depends on the current source value
			 * @param sourceV The container to populate the source value of the reverse in
			 * @param previousResultV The value containing the previous result of the transformation when it is available
			 * @param targetV The container to populate the target value of the reverse in
			 * @param target The target value to reverse
			 * @param modifications The {@link ExpressoTransformations.CombineWith} modifications to
			 *        {@link ExpressoTransformations.CombineWith.TransformationModification#prepareTransformOperation(TransformationValues)
			 *        prepare}
			 */
			protected static <S, T> void prepareTx(Transformation.TransformationValues<? extends S, ? extends T> tvs, boolean stateful,
				SettableValue<S> sourceV, SettableValue<T> previousResultV, SettableValue<T> targetV, T target,
				List<CombineWith.TransformationModification<S, T>> modifications) {
				if (stateful) {
					sourceV.set(tvs.getCurrentSource(), null);
					if (previousResultV != null)
						previousResultV.set(tvs.getPreviousResult());
				}
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

		/**
		 * A parameter object containing all information known to the {@link AbstractMapReverse} type
		 *
		 * @param <S> The source value type of the transformation
		 * @param <T> The target type of the transformation
		 */
		public static class ReverseParameters<S, T> {
			/** The container to publish the source value in */
			public final SettableValue<S> sourceValue;
			/** The container to publish the previously-evaluated result value in */
			public final SettableValue<T> previousResultValue;
			/** The container to publish the target value in */
			public final SettableValue<T> targetValue;
			/** All modifications added by {@link ExpressoTransformations.CombineWith}s */
			public final List<CombineWith.TransformationModification<S, T>> modifications;
			/** The transformation */
			public final Transformation<S, T> transformation;
			/** Whether and when the reverse operation is enabled */
			public final Function<Transformation.TransformationValues<? extends S, ? extends T>, String> enabledFn;
			/** Whether the reverse operation is enabled for a given target value */
			public final BiFunction<T, Transformation.TransformationValues<? extends S, ? extends T>, String> acceptFn;
			/** Whether this reverse operation can add values to a collection */
			public final TriFunction<T, Transformation.TransformationValues<? extends S, ? extends T>, Boolean, S> addFn;
			/** Whether the reverse operation can add a particular value to a collection */
			public final BiFunction<T, Transformation.TransformationValues<? extends S, ? extends T>, String> addAcceptFn;
			/** Whether the reverse operation depends on the current source value */
			public final boolean stateful;

			/**
			 * @param sourceValue The container to publish the source value in
			 * @param previousResultValue The container to publish the previously-evaluated result value in
			 * @param targetValue The container to publish the target value in
			 * @param modifications All modifications added by {@link ExpressoTransformations.CombineWith}s
			 * @param transformation The transformation
			 * @param enabledFn Whether and when the reverse operation is enabled
			 * @param acceptFn Whether the reverse operation is enabled for a given target value
			 * @param addFn Whether this reverse operation can add values to a collection
			 * @param addAcceptFn Whether the reverse operation can add a particular value to a collection
			 * @param stateful Whether the reverse operation depends on the current source value
			 */
			public ReverseParameters(SettableValue<S> sourceValue, SettableValue<T> previousResultValue, SettableValue<T> targetValue, //
				List<CombineWith.TransformationModification<S, T>> modifications, //
				Transformation<S, T> transformation, //
				Function<TransformationValues<? extends S, ? extends T>, String> enabledFn,
				BiFunction<T, TransformationValues<? extends S, ? extends T>, String> acceptFn,
				TriFunction<T, TransformationValues<? extends S, ? extends T>, Boolean, S> addFn,
				BiFunction<T, TransformationValues<? extends S, ? extends T>, String> addAcceptFn, boolean stateful) {
				this.sourceValue = sourceValue;
				this.previousResultValue = previousResultValue;
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

	/**
	 * A map-reverse operation that creates a new source value to map to a given new transformed value
	 *
	 * @param <E> The type of expresso element created
	 */
	@ExElementTraceable(toolkit = ExpressoBaseV0_1.BASE,
		qonfigType = "replace-source",
		interpretation = SourceReplaceReverse.Interpreted.class)
	public static class SourceReplaceReverse<E extends ExElement> extends AbstractMapReverse<E> {
		private boolean isInexact;

		/**
		 * @param parent The parent &lt;map-to> element
		 * @param qonfigType The Qonfig type of this add-on
		 */
		public SourceReplaceReverse(CompiledTransformation<?, ?, ?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType);
		}

		@Override
		public CompiledTransformation<?, ?, ?> getParentElement() {
			return super.getParentElement();
		}

		/** @return See {@link org.observe.Transformation.MappingSourceReplacingReverse#allowInexactReverse(boolean)} */
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

		/**
		 * {@link SourceReplaceReverse} interpretation
		 *
		 * @param <S> The source type of the transformation
		 * @param <T> The target type of the transformation
		 * @param <E> The type of expresso element created
		 */
		public static class Interpreted<S, T, E extends ExElement> extends AbstractMapReverse.Interpreted<S, T, E> {
			private InterpretedValueSynth<SettableValue<?>, SettableValue<S>> theReverse;

			Interpreted(SourceReplaceReverse<? super E> definition, ExElement.Interpreted<?> parent) {
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
			public void update(ModelComponentId sourceName, TypeToken<S> sourceType, TypeToken<T> targetType)
				throws ExpressoInterpretationException {
				super.update(sourceName, sourceType, targetType);
				theReverse = interpret(getDefinition().getReverse(), ModelTypes.Value.forType(sourceType));
			}

			@Override
			public Instantiator<S, T> instantiate() throws ModelInstantiationException {
				return new Instantiator<>(instantiateLocalModels(), getDefinition().getTargetVariable(), //
					getEnabled() == null ? null : getEnabled().instantiate(), getAccept() == null ? null : getAccept().instantiate(), //
						getAdd() == null ? null : getAdd().instantiate(), getAddAccept() == null ? null : getAddAccept().instantiate(),
							getDefinition().isStateful(), getReverse().instantiate(), getDefinition().isInexact());
			}
		}

		/**
		 * {@link SourceReplaceReverse} instantiator
		 *
		 * @param <S> The source type of the transformation
		 * @param <T> The target type of the transformation
		 */
		public static class Instantiator<S, T> extends AbstractMapReverse.Instantiator<S, T> {
			private final boolean isInexact;

			Instantiator(DocumentMap<ModelInstantiator> localModel, ModelComponentId targetVariable,
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
				models = wrapModels(models);
				SettableValue<S> sourceV = parameters.sourceValue;
				SettableValue<T> previousResultV = parameters.previousResultValue;
				SettableValue<T> targetV = parameters.targetValue;
				List<CombineWith.TransformationModification<S, T>> mods = parameters.modifications;
				boolean stateful = parameters.stateful;

				SettableValue<S> reversedEvld = getReverse().get(models);
				BiFunction<T, Transformation.TransformationValues<? extends S, ? extends T>, S> reverseFn;
				reverseFn = LambdaUtils.printableBiFn((target, tvs) -> {
					prepareTx(tvs, stateful, sourceV, previousResultV, targetV, target, mods);
					return reversedEvld.get();
				}, reversedEvld::toString, reversedEvld);
				return new Transformation.SourceReplacingReverse<>(parameters.transformation, reverseFn, parameters.enabledFn,
					parameters.acceptFn, parameters.addFn, parameters.addAcceptFn, stateful, isInexact);
			}
		}
	}

	/**
	 * A map-reverse operation that modifies the source value so that the transformation produces a given transformed value
	 *
	 * @param <E> The type of expresso element created
	 */
	@ExElementTraceable(toolkit = ExpressoBaseV0_1.BASE,
		qonfigType = "modify-source",
		interpretation = SourceModifyingReverse.Interpreted.class)
	public static class SourceModifyingReverse<E extends ExElement> extends AbstractMapReverse<E> {
		private boolean isSourceModifying;

		/**
		 * @param parent The &lt;map-to> parent for this map-reverse
		 * @param qonfigType The Qonfig type of this add-on
		 */
		public SourceModifyingReverse(CompiledTransformation<?, ?, ?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType);
		}

		/** @return Whether this reverse operation requires propagation of the updated value to the source */
		@QonfigAttributeGetter("requires-source-modification")
		public boolean requiresSourceModification() {
			return isSourceModifying;
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			super.doUpdate(session);

			isSourceModifying = session.getAttribute("requires-source-modification", boolean.class);
		}

		@Override
		public <S, T> Interpreted<S, T, ? extends E> interpret(ExElement.Interpreted<?> parent) throws ExpressoInterpretationException {
			return new Interpreted<>(this, parent);
		}

		/**
		 * {@link SourceModifyingReverse} interpretation
		 *
		 * @param <S> The source type of the transformation
		 * @param <T> The target type of the transformation
		 * @param <E> The type of expresso element created
		 */
		public static class Interpreted<S, T, E extends ExElement> extends AbstractMapReverse.Interpreted<S, T, E> {
			private InterpretedValueSynth<ObservableAction, ObservableAction> theReverse;

			Interpreted(SourceModifyingReverse<? super E> definition, ExElement.Interpreted<?> parent) {
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
			public void update(ModelComponentId sourceName, TypeToken<S> sourceType, TypeToken<T> targetType)
				throws ExpressoInterpretationException {
				super.update(sourceName, sourceType, targetType);
				theReverse = interpret(getDefinition().getReverse(), ModelTypes.Action.instance());
			}

			@Override
			public Instantiator<S, T> instantiate() throws ModelInstantiationException {
				return new Instantiator<>(instantiateLocalModels(), getDefinition().getTargetVariable(), //
					getEnabled() == null ? null : getEnabled().instantiate(), getAccept() == null ? null : getAccept().instantiate(), //
						getAdd() == null ? null : getAdd().instantiate(), getAddAccept() == null ? null : getAddAccept().instantiate(),
							getDefinition().isStateful(), getReverse().instantiate(), getDefinition().requiresSourceModification());
			}
		}

		/**
		 * {@link SourceModifyingReverse} instantiator
		 *
		 * @param <S> The source type of the transformation
		 * @param <T> The target type of the transformation
		 */
		public static class Instantiator<S, T> extends AbstractMapReverse.Instantiator<S, T> {
			private boolean isSourceModifying;

			Instantiator(DocumentMap<ModelInstantiator> localModel, ModelComponentId targetVariable,
				ModelValueInstantiator<SettableValue<String>> enabled, ModelValueInstantiator<SettableValue<String>> accept,
				ModelValueInstantiator<SettableValue<S>> add, ModelValueInstantiator<SettableValue<String>> addAccept, boolean stateful,
				ModelValueInstantiator<ObservableAction> reverse, boolean sourceModifying) {
				super(localModel, targetVariable, enabled, accept, add, addAccept, stateful, reverse);
				isSourceModifying = sourceModifying;
			}

			@Override
			public ModelValueInstantiator<ObservableAction> getReverse() {
				return (ModelValueInstantiator<ObservableAction>) super.getReverse();
			}

			@Override
			protected TransformReverse<S, T> createReverse(ReverseParameters<S, T> parameters, ModelSetInstance models)
				throws ModelInstantiationException {
				models = wrapModels(models);
				SettableValue<S> sourceV = parameters.sourceValue;
				SettableValue<T> previousResultV = parameters.previousResultValue;
				SettableValue<T> targetV = parameters.targetValue;
				List<CombineWith.TransformationModification<S, T>> mods = parameters.modifications;

				ObservableAction reversedEvld = getReverse().get(models);
				BiConsumer<T, Transformation.TransformationValues<? extends S, ? extends T>> reverseFn;
				reverseFn = (target, tvs) -> {
					prepareTx(tvs, true, sourceV, previousResultV, targetV, target, mods);
					reversedEvld.act(null);
				};
				return new Transformation.SourceModifyingReverse<>(reverseFn, parameters.enabledFn, parameters.acceptFn, parameters.addFn,
					parameters.addAcceptFn, isSourceModifying);
			}
		}
	}

	/**
	 * @param ex The reverse expression to test
	 * @param sourceName The name of the variable that the source value is published in
	 * @return Whether an expression refers to the source value of a transformation
	 */
	public static boolean refersToSource(ObservableExpression ex, String sourceName) {
		return !ex.find(ex2 -> ex2 instanceof NameExpression && ((NameExpression) ex2).getNames().getFirst().getName().equals(sourceName))
			.isEmpty();
	}

	/** A simple operation that returns a value */
	public static abstract class ScalarOp extends ExElement.Def.Abstract<ExElement.Void> {
		private ModelComponentId theSourceAsVariable;

		/**
		 * @param parent The parent of this element
		 * @param qonfigType The Qonfig type of this element
		 */
		protected ScalarOp(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType);
		}

		/** @return The model variable by which expressions can refer to the source value of this operation */
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
				elModels.<Interpreted<?, ?>, SettableValue<?>> satisfyElementSingleValueType(theSourceAsVariable, ModelTypes.Value,
					Interpreted::getSourceValueType);
			} else
				theSourceAsVariable = null;
		}

		/**
		 * @param parent The parent for the interpreted operation
		 * @return The interpreted operation
		 */
		public abstract Interpreted<?, ?> interpret(ExElement.Interpreted<?> parent);

		/**
		 * {@link ScalarOp} interpretation
		 *
		 * @param <S> The source type of the operation
		 * @param <T> The target type of the operation
		 */
		public static abstract class Interpreted<S, T> extends ExElement.Interpreted.Abstract<ExElement.Void> {
			private TypeToken<S> theSourceValueType;
			private TypeToken<T> theTargetValueType;

			/**
			 * @param definition The definition to interpret
			 * @param parent The parent of this element
			 */
			protected Interpreted(ScalarOp definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public ScalarOp getDefinition() {
				return (ScalarOp) super.getDefinition();
			}

			/** @return The source type of the operation */
			public TypeToken<S> getSourceValueType() {
				return theSourceValueType;
			}

			/** @return The target type of the operation */
			public TypeToken<T> getTargetValueType() {
				return theTargetValueType;
			}

			/**
			 * Initializes or updates this operation
			 *
			 * @param sourceType The source type of the operation
			 * @throws ExpressoInterpretationException If this operation could not be interpreted
			 */
			protected void updateOp(TypeToken<S> sourceType) throws ExpressoInterpretationException {
				theSourceValueType = sourceType;
				update();
				if (theTargetValueType == null)
					theTargetValueType = evaluateTargetType();
			}

			@Override
			protected void doUpdate() throws ExpressoInterpretationException {
				super.doUpdate();
				theTargetValueType = null;
				ExTyped.Interpreted<?> typed = getAddOn(ExTyped.Interpreted.class);
				if (typed != null)
					theTargetValueType = (TypeToken<T>) typed.getValueType();
				else
					theTargetValueType = getDefaultEnv().get(ExTyped.VALUE_TYPE_KEY, TypeToken.class);
			}

			/** @return The target type of the operation */
			protected abstract TypeToken<T> evaluateTargetType();

			/** @return The expression components of this operation */
			public abstract BetterList<InterpretedValueSynth<?, ?>> getComponents();

			/**
			 * @return The operation instantiator
			 * @throws ModelInstantiationException If the operation could not be instantiated
			 */
			public abstract Instantiator<S, T> instantiate() throws ModelInstantiationException;
		}

		/**
		 * {@link ScalarOp} instantiator
		 *
		 * @param <S> The source type of the operation
		 * @param <T> The target type of the operation
		 */
		public static abstract class Instantiator<S, T> {
			private final DocumentMap<ModelInstantiator> theLocalModels;
			private final ModelComponentId theSourceAsVariable;

			/** @param interpreted The interpretation to instantiate */
			protected Instantiator(ScalarOp.Interpreted<S, T> interpreted) {
				theLocalModels = interpreted.instantiateLocalModels();
				theSourceAsVariable = interpreted.getDefinition().getSourceAs();
			}

			/**
			 * Initializes model values in this instantiator
			 *
			 * @throws ModelInstantiationException If any model values fail to initialize
			 */
			public void instantiate() throws ModelInstantiationException {
				theLocalModels.forEach(ModelInstantiator::instantiate);
			}

			/**
			 * @param source The source value to transform
			 * @param models The model instance to get model values from
			 * @return The transformed value
			 * @throws ModelInstantiationException If the transformed value could not be produced
			 */
			public SettableValue<T> get(SettableValue<S> source, ModelSetInstance models) throws ModelInstantiationException {
				if (theLocalModels != null) {
					models = theLocalModels.operate(models, (m, mi) -> mi.wrap(m));
					if (theSourceAsVariable != null)
						ExFlexibleElementModelAddOn.satisfyElementValue(theSourceAsVariable, models, source);
				}
				return doGet(source, models);
			}

			/**
			 * @param sourceModels The source models to compare
			 * @param newModels The new models to compare
			 * @return Whether this operation differs from one model instance to the next
			 * @throws ModelInstantiationException If the difference could not be determined
			 */
			public boolean isDifferent(ModelSetInstance sourceModels, ModelSetInstance newModels) throws ModelInstantiationException {
				if (theLocalModels != null) {
					sourceModels = theLocalModels.operate(sourceModels, (m, mi) -> mi.wrap(m));
					newModels = theLocalModels.operate(newModels, (m, mi) -> mi.wrap(m));
				}
				return checkIsDifferent(sourceModels, newModels);
			}

			/**
			 * @param source The source value to transform
			 * @param models The model instance to get model values from
			 * @return The transformed value
			 * @throws ModelInstantiationException If the transformed value could not be produced
			 */
			protected abstract SettableValue<T> doGet(SettableValue<S> source, ModelSetInstance models) throws ModelInstantiationException;

			/**
			 * @param sourceModels The source models to compare
			 * @param newModels The new models to compare
			 * @return Whether this operation differs from one model instance to the next
			 * @throws ModelInstantiationException If the difference could not be determined
			 */
			protected abstract boolean checkIsDifferent(ModelSetInstance sourceModels, ModelSetInstance newModels)
				throws ModelInstantiationException;
		}
	}

	/** &lt;if> operation, produces values based on conditions against the source */
	@ExElementTraceable(toolkit = ExpressoBaseV0_1.BASE, qonfigType = If.IF, interpretation = If.Interpreted.class)
	public static class If extends ScalarOp {
		/** The XML name of this operation */
		public static final String IF = "if";

		private CompiledExpression theValue;
		private final List<ScalarOp> theIfs;

		/**
		 * @param parent The parent element for this operation
		 * @param qonfigType The Qonfig type of this element
		 */
		public If(Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType);
			theIfs = new ArrayList<>();
		}

		/** @return The value to return if none of the child "if" conditions are met */
		@QonfigAttributeGetter()
		public CompiledExpression getValue() {
			return theValue;
		}

		/** @return Sub-values whose value will be returned if their "if" condition is true */
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

		/**
		 * {@link If} interpretation
		 *
		 * @param <S> The source type of the operation
		 * @param <T> The target type of the operation
		 */
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

			/** @return The value to return if none of the child "if" conditions are met */
			public InterpretedValueSynth<SettableValue<?>, SettableValue<T>> getValue() {
				return theValue;
			}

			/** @return Sub-values whose value will be returned if their "if" condition is true */
			public List<ScalarOp.Interpreted<S, T>> getIfs() {
				return Collections.unmodifiableList(theIfs);
			}

			@Override
			protected void doUpdate() throws ExpressoInterpretationException {
				super.doUpdate();

				theValue = interpret(getDefinition().getValue(),
					getTargetValueType() == null ? ModelTypes.Value.anyAsV() : ModelTypes.Value.forType(getTargetValueType()));
				ExTyped.Interpreted<T> typed = getAddOn(ExTyped.Interpreted.class);
				Transaction typeConfig = Transaction.NONE;
				if (typed != null && typed.getDefinition().getValueType() != null) {
					// If the type is specified, tell all the children to evaluate with that type
					typeConfig = getDefaultEnv().putTemp(ExTyped.VALUE_TYPE_KEY, typed.getValueType());
				}
				try {
					syncChildren(getDefinition().getIfs(), theIfs, iff -> (ScalarOp.Interpreted<S, T>) iff.interpret(this),
						iff -> iff.updateOp(getSourceValueType()));
				} finally {
					typeConfig.close();
				}
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

		/**
		 * {@link If} instantiator
		 *
		 * @param <S> The source type of the operation
		 * @param <T> The target type of the operation
		 */
		protected static class Instantiator<S, T> extends ScalarOp.Instantiator<S, T> {
			private final ModelValueInstantiator<SettableValue<T>> theValue;
			private final List<ModelValueInstantiator<SettableValue<Boolean>>> theIfConditions;
			private final List<ScalarOp.Instantiator<S, T>> theIfs;
			private final String theAlias;

			Instantiator(Interpreted<S, T> interpreted) throws ModelInstantiationException {
				super(interpreted);
				theValue = interpreted.getValue().instantiate();
				theIfConditions = new ArrayList<>(interpreted.getIfs().size());
				theIfs = new ArrayList<>(interpreted.getIfs().size());
				for (ScalarOp.Interpreted<S, T> iff : interpreted.getIfs()) {
					theIfConditions.add(iff.getAddOn(IfOp.Interpreted.class).getIf().instantiate());
					theIfs.add(iff.instantiate());
				}
				theAlias = interpreted.reporting().toString();
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
					ifs.add(ifCondition.map(LambdaUtils.printableFn(b -> new ConditionalValue<>(Boolean.TRUE.equals(b), iff),
						() -> "if(" + ifCondition + ", " + iff + ")", null)));
				}
				SettableValue<T> value = theValue.get(models);
				ifs.add(ObservableValue.of(new ConditionalValue<>(true, value)));
				ObservableValue<ConditionalValue<T>> firstTrue = ObservableValue.<ConditionalValue<T>> firstValue(//
					LambdaUtils.printablePred(cv -> cv.condition, "condition", null), null, ifs.toArray(new ObservableValue[ifs.size()]));
				return SettableValue
					.flatten(firstTrue.map(LambdaUtils.printableFn(cv -> {
						if (cv == null) {
							System.err.println("Null condition value?");
							return null;
						}
						return cv.value;
					}, "value", null)))//
					.alias(theAlias);
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
				for (ModelValueInstantiator<SettableValue<Boolean>> ifC : theIfConditions) {
					if (ifC.get(sourceModels) != ifC.get(models))
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

	/** Add-on for a conditional operation, like a collection filter */
	@ExElementTraceable(toolkit = ExpressoBaseV0_1.BASE, qonfigType = IfOp.IF_OP, interpretation = IfOp.Interpreted.class)
	public static class IfOp extends ExAddOn.Def.Abstract<ExElement, ExAddOn.Void<ExElement>> {
		/** The XML name of this operation */
		public static final String IF_OP = "if-op";

		private CompiledExpression theIf;

		/**
		 * @param type The Qonfig type of this add-on
		 * @param element The element this if-op affects
		 */
		public IfOp(QonfigAddOn type, ExElement.Def<?> element) {
			super(type, element);
		}

		@Override
		public Set<? extends Class<? extends ExAddOn.Def<?, ?>>> getDependencies() {
			return (Set<Class<ExAddOn.Def<?, ?>>>) (Set<?>) Collections.singleton(ExModelAugmentation.Def.class);
		}

		/** @return The condition to apply to the source value */
		@QonfigAttributeGetter("if")
		public CompiledExpression getIf() {
			return theIf;
		}

		@Override
		public void update(ExpressoQIS session, ExElement.Def<?> element) throws QonfigInterpretationException {
			super.update(session, element);

			theIf = getElement().getAttributeExpression("if", session);
		}

		@Override
		public <E2 extends ExElement> Interpreted interpret(ExElement.Interpreted<E2> element) {
			return new Interpreted(this, element);
		}

		/** {@link IfOp} interpretation */
		public static class Interpreted extends ExAddOn.Interpreted.Abstract<ExElement, ExAddOn.Void<ExElement>> {
			private InterpretedValueSynth<SettableValue<?>, SettableValue<Boolean>> theIf;

			Interpreted(IfOp definition, ExElement.Interpreted<?> element) {
				super(definition, element);
			}

			@Override
			public IfOp getDefinition() {
				return (IfOp) super.getDefinition();
			}

			/** @return The condition to apply to the source value */
			public InterpretedValueSynth<SettableValue<?>, SettableValue<Boolean>> getIf() {
				return theIf;
			}

			@Override
			public void update(ExElement.Interpreted<?> element) throws ExpressoInterpretationException {
				super.update(element);

				theIf = getElement().interpret(getDefinition().getIf(), ModelTypes.Value.BOOLEAN);
			}

			@Override
			public Class<ExAddOn.Void<ExElement>> getInstanceType() {
				return (Class<ExAddOn.Void<ExElement>>) (Class<?>) ExAddOn.Void.class;
			}

			@Override
			public ExAddOn.Void<ExElement> create(ExElement element) {
				return null;
			}
		}
	}

	/** &lt;switch> operation, produces values by source value */
	@ExElementTraceable(toolkit = ExpressoBaseV0_1.BASE, qonfigType = Switch.SWITCH, interpretation = Switch.Interpreted.class)
	public static class Switch extends ScalarOp {
		/** The XML name of this operation */
		public static final String SWITCH = "switch";

		private CompiledExpression theDefault;
		private final List<ScalarOp> theCases;

		/**
		 * @param parent The parent element for this operation
		 * @param qonfigType The Qonfig type of this element
		 */
		public Switch(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType);
			theCases = new ArrayList<>();
		}

		/** @return The value to use if the source value does not match any cases */
		@QonfigAttributeGetter("default")
		public CompiledExpression getDefault() {
			return theDefault;
		}

		/** @return The cases to determine the value if the source value matches the case value */
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

		/**
		 * {@link Switch} interpretation
		 *
		 * @param <S> The source type of the operation
		 * @param <T> The target type of the operation
		 */
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

			/** @return The value to use if the source value does not match any cases */
			public InterpretedValueSynth<SettableValue<?>, SettableValue<T>> getDefault() {
				return theDefault;
			}

			/** @return The cases to determine the value if the source value matches the case value */
			public List<ScalarOp.Interpreted<S, T>> getCases() {
				return Collections.unmodifiableList(theCases);
			}

			@Override
			protected void doUpdate() throws ExpressoInterpretationException {
				super.doUpdate();

				theDefault = interpret(getDefinition().getDefault(), //
					getTargetValueType() == null ? ModelTypes.Value.anyAsV() : ModelTypes.Value.forType(getTargetValueType()));
				ExTyped.Interpreted<T> typed = getAddOn(ExTyped.Interpreted.class);
				Transaction typeConfig = Transaction.NONE;
				if (typed != null && typed.getDefinition().getValueType() != null) {
					// If the type is specified, tell all the children to evaluate with that type
					typeConfig = getDefaultEnv().putTemp(ExTyped.VALUE_TYPE_KEY, typed.getValueType());
				}
				try {
					syncChildren(getDefinition().getCases(), theCases, def -> (ScalarOp.Interpreted<S, T>) def.interpret(this),
						interp -> interp.updateOp(getSourceValueType()));
				} finally {
					typeConfig.close();
				}
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
			private final ModelValueInstantiator<SettableValue<T>> theDefault;
			private final List<ModelValueInstantiator<SettableValue<S>>> theCaseValues;
			private final List<ScalarOp.Instantiator<S, T>> theCases;
			private final String theAlias;

			Instantiator(Interpreted<S, T> interpreted) throws ModelInstantiationException {
				super(interpreted);
				theDefault = interpreted.getDefault().instantiate();
				theCaseValues = new ArrayList<>(interpreted.getCases().size());
				theCases = new ArrayList<>(interpreted.getCases().size());
				for (ScalarOp.Interpreted<S, T> caase : interpreted.getCases()) {
					theCaseValues.add(caase.getAddOn(CaseOp.Interpreted.class).getCase().instantiate());
					theCases.add(caase.instantiate());
				}
				theAlias = interpreted.reporting().toString();
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
				ObservableMap<S, SettableValue<T>> map = ObservableCollection.of(caseTuples)//
					.flow()//
					.flattenValues(LambdaUtils.identity())//
					.<S> groupBy(cv -> cv.caseValue, null)//
					.<SettableValue<T>> withValues(values -> values.map(cv -> cv.value))//
					.gatherActive(models.getUntil())//
					.singleMap(true);

				ObservableValue<ObservableValue<SettableValue<T>>> mapValue = source.map(s -> map.observe(s));
				SettableValue<T> def = theDefault.get(models);
				ObservableValue<SettableValue<T>> caseOrDefault = ObservableValue.firstValue(sv -> sv != null, null,
					ObservableValue.flatten(mapValue), ObservableValue.of(def));
				return SettableValue.flatten(caseOrDefault).alias(theAlias);
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

	/** A case in a {@link Switch} operation */
	@ExElementTraceable(toolkit = ExpressoBaseV0_1.BASE, qonfigType = CaseOp.CASE_OP, interpretation = CaseOp.Interpreted.class)
	public static class CaseOp extends ExAddOn.Def.Abstract<ExElement, ExAddOn.Void<ExElement>> {
		/** The XML name of this element */
		public static final String CASE_OP = "case-op";

		private CompiledExpression theCase;

		/**
		 * @param type The Qonfig type of this add-on
		 * @param element The element to produce the value if the source value matches the case
		 */
		public CaseOp(QonfigAddOn type, ExElement.Def<?> element) {
			super(type, element);
		}

		/** @return The value to test against the source value to determine if this element will produce the target value */
		@QonfigAttributeGetter("case")
		public CompiledExpression getCase() {
			return theCase;
		}

		@Override
		public Set<? extends Class<? extends ExAddOn.Def<?, ?>>> getDependencies() {
			return (Set<Class<ExAddOn.Def<?, ?>>>) (Set<?>) Collections.singleton(ExModelAugmentation.Def.class);
		}

		@Override
		public void update(ExpressoQIS session, ExElement.Def<?> element) throws QonfigInterpretationException {
			super.update(session, element);

			theCase = getElement().getAttributeExpression("case", session);
		}

		@Override
		public <E2 extends ExElement> Interpreted<?> interpret(ExElement.Interpreted<E2> element) {
			return new Interpreted<>(this, element);
		}

		/**
		 * {@link CaseOp} interpretation
		 *
		 * @param <S> The source type of the operation
		 */
		public static class Interpreted<S> extends ExAddOn.Interpreted.Abstract<ExElement, ExAddOn.Void<ExElement>> {
			private InterpretedValueSynth<SettableValue<?>, SettableValue<S>> theCase;

			Interpreted(CaseOp definition, ExElement.Interpreted<?> element) {
				super(definition, element);
			}

			@Override
			public CaseOp getDefinition() {
				return (CaseOp) super.getDefinition();
			}

			/** @return The value to test against the source value to determine if this element will produce the target value */
			public InterpretedValueSynth<SettableValue<?>, SettableValue<S>> getCase() {
				return theCase;
			}

			@Override
			public void update(ExElement.Interpreted<?> element) throws ExpressoInterpretationException {
				super.update(element);

				theCase = getElement().interpret(getDefinition().getCase(),
					ModelTypes.Value.forType(((ScalarOp.Interpreted<S, ?>) element).getSourceValueType()));
			}

			@Override
			public Class<ExAddOn.Void<ExElement>> getInstanceType() {
				return (Class<ExAddOn.Void<ExElement>>) (Class<?>) ExAddOn.Void.class;
			}

			@Override
			public ExAddOn.Void<ExElement> create(ExElement element) {
				return null;
			}
		}
	}

	/** Simple operation that just returns a value */
	@ExElementTraceable(toolkit = ExpressoBaseV0_1.BASE, qonfigType = Return.RETURN, interpretation = Return.Interpreted.class)
	public static class Return extends ScalarOp {
		/** The XML name of this element */
		public static final String RETURN = "return";

		private CompiledExpression theValue;

		/**
		 * @param parent The parent of this element
		 * @param qonfigType The Qonfig type of this element
		 */
		public Return(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType);
		}

		/** @return The value for the result */
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

		/**
		 * {@link Return} interpretation
		 *
		 * @param <S> The source type of the operation
		 * @param <T> The target type of the operation
		 */
		public static class Interpreted<S, T> extends ScalarOp.Interpreted<S, T> {
			private InterpretedValueSynth<SettableValue<?>, SettableValue<T>> theValue;

			Interpreted(Return definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public Return getDefinition() {
				return (Return) super.getDefinition();
			}

			/** @return The value for the result */
			public InterpretedValueSynth<SettableValue<?>, SettableValue<T>> getValue() {
				return theValue;
			}

			@Override
			protected void doUpdate() throws ExpressoInterpretationException {
				super.doUpdate();

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
