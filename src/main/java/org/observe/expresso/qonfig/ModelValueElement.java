package org.observe.expresso.qonfig;

import java.util.Iterator;

import org.observe.expresso.CompiledExpressoEnv;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.InterpretedExpressoEnv;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelType;
import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.expresso.ObservableExpression;
import org.observe.expresso.ObservableModelSet;
import org.observe.expresso.ObservableModelSet.CompiledModelValue;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelValueInstantiator;
import org.observe.expresso.VariableType;
import org.observe.util.TypeTokens;
import org.qommons.Transaction;
import org.qommons.collect.CircularArrayList;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;

import com.google.common.reflect.TypeToken;

/**
 * <p>
 * This class is the value component of the unification of {@link ObservableModelSet} with {@link ExElement}. {@link ObservableModelElement}
 * is the model component of the unification.
 * </p>
 *
 * <p>
 * The {@link ModelValueElement.CompiledSynth CompiledSynth} interface extends both a {@link ExElement.Def ExElement.Def} and
 * {@link org.observe.expresso.ObservableModelSet.CompiledModelValue ObservableModelSet.CompiledModelValue}.
 * </p>
 *
 * <p>
 * CompiledSynth's {@link ModelValueElement.CompiledSynth#interpret(InterpretedExpressoEnv) interpret} method produces an
 * {@link ModelValueElement.InterpretedSynth InterpretedSynth} instance, which extends both {@link ExElement.Interpreted} and
 * {@link org.observe.expresso.ObservableModelSet.InterpretedValueSynth ObservableModelSet.InterpretedValueSynth}.
 * </p>
 * <p>
 * InterpretedSynth's {@link ModelValueElement.InterpretedSynth#create() create} method creates a {@link ModelValueElement}, which extends
 * both {@link ExElement} and {@link ModelValueInstantiator}.
 * </p>
 *
 * @param <MV> The instance type of the model value to instantiate
 */
public interface ModelValueElement<MV> extends ExElement, ModelValueInstantiator<MV> {
	/** Session key containing a model value's path */
	String PATH_KEY = "model-path";

	/** Mechanism for resolving interpreted element parents from lower on the call stack */
	public static class ModelValueParent {
		private final ThreadLocal<CircularArrayList<ExElement.Interpreted<?>>> theParents;

		/** Creates the parent resolver */
		public ModelValueParent() {
			theParents = ThreadLocal.withInitial(CircularArrayList::new);
		}

		/**
		 * @param parent The parent to install on the call stack
		 * @return A transaction whose {@link Transaction#close() close} method will remove the parent from the call stack
		 */
		public Transaction installParent(ExElement.Interpreted<?> parent) {
			CircularArrayList<ExElement.Interpreted<?>> parents = theParents.get();
			parents.add(parent);
			return parents::removeLast;
		}

		/**
		 * @param definition The definition of the parent to get
		 * @return The parent on the current call chain with the given {@link ExElement.Interpreted#getDefinition() definition}
		 */
		public ExElement.Interpreted<?> getParent(ExElement.Def<?> definition) {
			if (definition == null)
				return null;
			Iterator<ExElement.Interpreted<?>> iter = theParents.get().descendingIterator();
			while (iter.hasNext()) {
				ExElement.Interpreted<?> parent = iter.next();
				if (parent.getDefinition() == definition)
					return parent;
				else if (parent instanceof ObservableModelElement.Interpreted && definition instanceof ObservableModelElement.Def) {
					parent = ((ObservableModelElement.Interpreted<?>) parent).getInterpretingModel(//
						((ObservableModelElement.Def<?, ?>) definition).getModelPath());
					if (parent != null && parent.getDefinition() == definition)
						return parent;
				}
			}
			return null;
		}
	}

	/** The parent resolver for element interpretations */
	public static final ModelValueParent INTERPRETING_PARENTS = new ModelValueParent();

	/**
	 * Definition for a {@link ModelValueElement}
	 *
	 * @param <M> The model type of the value
	 * @param <E> The sub-type of {@link ModelValueElement} to create
	 */
	@ExElementTraceable(toolkit = ExpressoSessionImplV0_1.CORE, qonfigType = "model-value", interpretation = Interpreted.class)
	public interface Def<M, E extends ModelValueElement<?>> extends ExElement.Def<E> {
		/** @return The model path of this model value (e.g. "app.value") */
		String getModelPath();

		/**
		 * @param env The expresso environment to use to interpret expressions
		 * @return The model type of the value to create
		 */
		ModelType<M> getModelType(CompiledExpressoEnv env);

		@Override
		@QonfigAttributeGetter
		CompiledExpression getElementValue();

		/**
		 * Populates a model builder with this element's value
		 *
		 * @param builder The model builder to populate
		 * @param session The interpretation session to use to parse Qonfig members
		 * @throws QonfigInterpretationException If this model value cannot be interpreted
		 */
		void populate(ObservableModelSet.Builder builder, ExpressoQIS session) throws QonfigInterpretationException;

		/**
		 * Prepares the model value
		 *
		 * @param session The interpretation session to use to parse Qonfig members
		 * @throws QonfigInterpretationException If this model value cannot be interpreted
		 */
		void prepareModelValue(ExpressoQIS session) throws QonfigInterpretationException;

		/**
		 * Abstract {@link ModelValueElement} definition implementation
		 *
		 * @param <M> The model type of the value to create
		 * @param <E> The sub-type of {@link ModelValueElement} to create
		 */
		public abstract class Abstract<M, E extends ModelValueElement<?>> extends ExElement.Def.Abstract<E> implements Def<M, E> {
			private final ModelType<M> theModelType;
			private CompiledExpression theValue;
			private String theModelPath;
			private boolean isPrepared;

			/**
			 * @param parent The parent element of this element
			 * @param qonfigType The Qonfig type of this element
			 * @param modelType The model type of this value
			 */
			protected Abstract(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType, ModelType<M> modelType) {
				super(parent, qonfigType);
				theModelType = modelType;
			}

			@Override
			public ModelType<M> getModelType(CompiledExpressoEnv env) {
				return theModelType;
			}

			@Override
			public String getModelPath() {
				return theModelPath;
			}

			@QonfigAttributeGetter
			@Override
			public CompiledExpression getElementValue() {
				return theValue;
			}

			@Override
			protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
				isPrepared = false;
				super.doUpdate(session);
				String name = getAddOnValue(ExNamed.Def.class, ExNamed.Def::getName);
				if (name != null) {
					theModelPath = session.get(ModelValueElement.PATH_KEY, String.class);
					if (theModelPath == null || theModelPath.isEmpty())
						theModelPath = name;
					else
						theModelPath += "." + name;
				}
				theValue = getValueExpression(session);
			}

			@Override
			public final void prepareModelValue(ExpressoQIS session) throws QonfigInterpretationException {
				if (isPrepared)
					return;
				isPrepared = true;
				doPrepare(session);
			}

			/**
			 * Performs implementation-specific model preparation on this value
			 *
			 * @param session The interpretation session to use to parse Qonfig members
			 * @throws QonfigInterpretationException If this model value cannot be interpreted
			 */
			protected abstract void doPrepare(ExpressoQIS session) throws QonfigInterpretationException;
		}

		/**
		 * Abstract {@link ModelValueElement} definition implementation for values with {@link org.observe.expresso.ModelType.SingleTyped
		 * singular} model types
		 *
		 * @param <M> The model type of the value to create
		 * @param <E> The sub-type of {@link ModelValueElement} to create
		 */
		public abstract class SingleTyped<M, E extends ModelValueElement<?>> extends Abstract<M, E> {
			private VariableType theValueType;

			/**
			 * @param parent The parent element of this element
			 * @param qonfigType The Qonfig type of this element
			 * @param modelType The model type of this value
			 */
			protected SingleTyped(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType, ModelType.SingleTyped<M> modelType) {
				super(parent, qonfigType, modelType);
			}

			@Override
			public ModelType.SingleTyped<M> getModelType(CompiledExpressoEnv env) {
				return (ModelType.SingleTyped<M>) super.getModelType(env);
			}

			/** @return The specified value type for the instance type */
			public VariableType getValueType() {
				return theValueType;
			}

			@Override
			protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
				super.doUpdate(session);
				theValueType = session.get(ExTyped.VALUE_TYPE_KEY, VariableType.class);
			}

			/** @return Whether to use wrapper types (e.g. {@link Integer} instead of int) for this model value */
			protected boolean useWrapperType() {
				return false;
			}

			/**
			 * Abstract {@link SingleTyped} interpretation
			 *
			 * @param <M> The model type of the value to create
			 * @param <MV> The instance type of the value to create
			 * @param <E> The sub-type of {@link ModelValueElement} to create
			 */
			public static abstract class Interpreted<M, MV extends M, E extends ModelValueElement<MV>>
			extends ModelValueElement.Interpreted.Abstract<M, MV, E> {
				private ModelInstanceType<M, MV> theTargetType;

				/**
				 * @param definition The definition to interpret
				 * @param parent The interpreted parent for this element
				 */
				protected Interpreted(SingleTyped<M, ? super E> definition, ExElement.Interpreted<?> parent) {
					super(definition, parent);
				}

				@Override
				public SingleTyped<M, ? super E> getDefinition() {
					return (SingleTyped<M, ? super E>) super.getDefinition();
				}

				@Override
				protected ModelInstanceType<M, MV> getTargetType() {
					return theTargetType;
				}

				@Override
				protected void doUpdate(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
					if (getDefinition().getValueType() != null) {
						TypeToken<?> valueType = getDefinition().getValueType().getType(env);
						if (getDefinition().useWrapperType())
							valueType = TypeTokens.get().wrap(valueType);
						theTargetType = (ModelInstanceType<M, MV>) getDefinition().getModelType(env).forTypes(valueType);
					} else
						theTargetType = getDefinition().getModelType(env).anyAs();
					super.doUpdate(env);
				}
			}
		}

		/**
		 * Abstract {@link ModelValueElement} definition implementation for values with {@link org.observe.expresso.ModelType.DoubleTyped
		 * double} model types
		 *
		 * @param <M> The model type of the value to create
		 * @param <E> The sub-type of {@link ModelValueElement} to create
		 */
		public abstract class DoubleTyped<M, E extends ModelValueElement<?>> extends Abstract<M, E> {
			private VariableType theValueType1;
			private VariableType theValueType2;

			/**
			 * @param parent The parent element of this element
			 * @param qonfigType The Qonfig type of this element
			 * @param modelType The model type of this value
			 */
			protected DoubleTyped(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType, ModelType.DoubleTyped<M> modelType) {
				super(parent, qonfigType, modelType);
			}

			@Override
			public ModelType.DoubleTyped<M> getModelType(CompiledExpressoEnv env) {
				return (ModelType.DoubleTyped<M>) super.getModelType(env);
			}

			/** @return The specified type for the first component of the instance type */
			public VariableType getValueType1() {
				return theValueType1;
			}

			/** @return The specified type for the second component of the instance type */
			public VariableType getValueType2() {
				return theValueType2;
			}

			@Override
			protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
				super.doUpdate(session);
				theValueType1 = session.get(ExMapModelValue.KEY_TYPE_KEY, VariableType.class);
				theValueType2 = session.get(ExTyped.VALUE_TYPE_KEY, VariableType.class);
			}

			/** @return Whether to use wrapper types (e.g. {@link Integer} instead of int) for this model value */
			protected boolean useWrapperType() {
				return false;
			}

			/**
			 * Abstract {@link DoubleTyped} interpretation
			 *
			 * @param <M> The model type of the value to create
			 * @param <MV> The instance type of the value to create
			 * @param <E> The sub-type of {@link ModelValueElement} to create
			 */
			public static abstract class Interpreted<M, MV extends M, E extends ModelValueElement<MV>>
			extends ModelValueElement.Interpreted.Abstract<M, MV, E> {
				private ModelInstanceType<M, MV> theTargetType;

				/**
				 * @param definition The definition to interpret
				 * @param parent The interpreted parent for this element
				 */
				protected Interpreted(DoubleTyped<M, ? super E> definition, ExElement.Interpreted<?> parent) {
					super(definition, parent);
				}

				@Override
				public DoubleTyped<M, ? super E> getDefinition() {
					return (DoubleTyped<M, ? super E>) super.getDefinition();
				}

				@Override
				protected ModelInstanceType<M, MV> getTargetType() {
					return theTargetType;
				}

				@Override
				protected void doUpdate(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
					ModelInstanceType<?, ?> type;
					if (getDefinition().getValueType1() != null) {
						TypeToken<?> valueType1 = getDefinition().getValueType1().getType(env);
						if (getDefinition().useWrapperType())
							valueType1 = TypeTokens.get().wrap(valueType1);
						if (getDefinition().getValueType2() != null) {
							TypeToken<?> valueType2 = getDefinition().getValueType2().getType(env);
							if (getDefinition().useWrapperType())
								valueType2 = TypeTokens.get().wrap(valueType2);
							type = getDefinition().getModelType(env).forTypes(valueType1, valueType2);
						} else
							type = getDefinition().getModelType(env).forTypes(getDefinition().getValueType1().getType(env),
								TypeTokens.get().WILDCARD);
					} else if (getDefinition().getValueType2() != null) {
						TypeToken<?> valueType2 = getDefinition().getValueType2().getType(env);
						if (getDefinition().useWrapperType())
							valueType2 = TypeTokens.get().wrap(valueType2);
						type = getDefinition().getModelType(env).forTypes(TypeTokens.get().WILDCARD, valueType2);
					} else
						type = getDefinition().getModelType(env).any();
					theTargetType = (ModelInstanceType<M, MV>) type;

					super.doUpdate(env);
				}
			}
		}
	}

	/**
	 * Interpretation for a {@link ModelValueElement}
	 *
	 * @param <M> The model type of the value
	 * @param <MV> The instance type of the value
	 * @param <E> The sub-type of {@link ModelValueElement} to create
	 */
	public interface Interpreted<M, MV extends M, E extends ModelValueElement<MV>> extends ExElement.Interpreted<E> {
		@Override
		Def<M, ? super E> getDefinition();

		/** @return The type of the value to create */
		ModelInstanceType<M, MV> getType();

		/** @return The interpreted expression in this element's element value */
		InterpretedValueSynth<?, ?> getElementValue();

		/**
		 * Initializes or updates this value
		 *
		 * @param env The expresso environment to use to interpret expressions
		 * @throws ExpressoInterpretationException If this value cannot be interpreted
		 */
		void updateValue(InterpretedExpressoEnv env) throws ExpressoInterpretationException;

		/**
		 * @return The instantiator for this value
		 * @throws ModelInstantiationException If the value could not be instantiated
		 */
		E create() throws ModelInstantiationException;

		/**
		 * Abstract {@link ModelValueElement} interpretation implementation
		 *
		 * @param <M> The model type of the value to create
		 * @param <MV> The instance type of the value to create
		 * @param <E> The sub-type of {@link ModelValueElement} to create
		 */
		public abstract class Abstract<M, MV extends M, E extends ModelValueElement<MV>> extends ExElement.Interpreted.Abstract<E>
		implements Interpreted<M, MV, E> {
			private InterpretedValueSynth<M, MV> theValue;

			/**
			 * @param definition The definition to interpret
			 * @param parent The interpreted parent for this element
			 */
			protected Abstract(Def<M, ? super E> definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public Def<M, ? super E> getDefinition() {
				return (Def<M, ? super E>) super.getDefinition();
			}

			@Override
			public InterpretedValueSynth<M, MV> getElementValue() {
				return theValue;
			}

			/** @return The type to attempt to interpret this element's element value as */
			protected abstract ModelInstanceType<M, MV> getTargetType();

			@Override
			public ModelInstanceType<M, MV> getType() {
				if (theValue != null)
					return theValue.getType();
				else
					return getTargetType();
			}

			@Override
			public void updateValue(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
				update(env);
			}

			@Override
			protected void doUpdate(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
				super.doUpdate(env);
				if (getDefinition().getElementValue() == null
					|| getDefinition().getElementValue().getExpression() == ObservableExpression.EMPTY)
					theValue = null;
				else
					theValue = interpret(getDefinition().getElementValue(), getTargetType());
			}
		}
	}

	/**
	 * Definition of a {@link ModelValueElement}. Most {@link ModelValueElement} definitions will implement this. Fulfills both
	 * {@link ExElement.Def ExElement.Def} and {@link org.observe.expresso.ObservableModelSet.CompiledModelValue
	 * ObservableModelSet.CompiledModelValue} roles.
	 *
	 * @param <M> The model type of the value to create
	 * @param <E> The sub-type of {@link ModelValueElement} to create
	 */
	public interface CompiledSynth<M, E extends ModelValueElement<?>> extends ModelValueElement.Def<M, E>, CompiledModelValue<M> {
		@Override
		default void populate(ObservableModelSet.Builder builder, ExpressoQIS session) throws QonfigInterpretationException {
			String name = getAddOnValue(ExNamed.Def.class, ExNamed.Def::getName);
			if (name == null)
				throw new QonfigInterpretationException("Not named, cannot add to model set", getElement().getPositionInFile(), 0);
			builder.withMaker(name, this, reporting().getFileLocation().getPosition(0));
		}

		@Override
		default InterpretedSynth<M, ?, ? extends E> interpret(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
			ExElement.Interpreted<?> parent = INTERPRETING_PARENTS.getParent(getParentElement());
			if (parent == null)
				throw new ExpressoInterpretationException("Correct model not installed in environment", reporting().getFileLocation());
			InterpretedSynth<M, ?, ? extends E> interpreted = interpretValue(parent);
			interpreted.updateValue(env);
			return interpreted;
		}

		/**
		 * Interprets this value
		 *
		 * @param parent The interpreted parent element (may not be available, i.e. will be null)
		 * @return The interpreted model value
		 */
		InterpretedSynth<M, ?, ? extends E> interpretValue(ExElement.Interpreted<?> parent);
	}

	/**
	 * Interpretation of a {@link ModelValueElement}. Moste {@link ModelValueElement} interpretations will implement this. Fulfills both
	 * {@link ExElement.Interpreted ExElement.Interpreted} and {@link org.observe.expresso.ObservableModelSet.InterpretedValueSynth
	 * ObservableModelSet.InterpretedValueSynth} roles.
	 *
	 * @param <M> The model type of the value to create
	 * @param <MV> The instance type of the value to create
	 * @param <E> The sub-type of {@link ModelValueElement} to create
	 */
	public interface InterpretedSynth<M, MV extends M, E extends ModelValueElement<MV>>
	extends ModelValueElement.Interpreted<M, MV, E>, InterpretedValueSynth<M, MV> {
		@Override
		default ModelValueElement<MV> instantiate() throws ModelInstantiationException {
			return create();
		}
	}

	/** @return The model path of this model value (e.g. "app.value") */
	String getModelPath();

	/** @return The interpreted expression in this element's element value */
	ModelValueInstantiator<?> getElementValue();

	@Override
	default void instantiate() throws ModelInstantiationException {
		instantiated();
	}

	/**
	 * Abstract {@link ModelValueElement} implementation
	 *
	 * @param <MV> The instance type of the value
	 */
	public abstract class Abstract<MV> extends ExElement.Abstract implements ModelValueElement<MV> {
		private final String theModelPath;
		private ModelValueInstantiator<?> theElementValue;

		/**
		 * @param interpreted The interpretation to instantiate
		 * @throws ModelInstantiationException If this value could not be instantiated
		 */
		protected Abstract(ModelValueElement.Interpreted<?, MV, ?> interpreted) throws ModelInstantiationException {
			super(interpreted.getIdentity());
			theModelPath = interpreted.getDefinition().getModelPath();
			theElementValue = interpreted.getElementValue() == null ? null : interpreted.getElementValue().instantiate();
		}

		@Override
		public String getModelPath() {
			return theModelPath;
		}

		@Override
		public ModelValueInstantiator<?> getElementValue() {
			return theElementValue;
		}

		@Override
		public void instantiated() throws ModelInstantiationException {
			super.instantiated();
			if (theElementValue != null)
				theElementValue.instantiate();
		}
	}
}
