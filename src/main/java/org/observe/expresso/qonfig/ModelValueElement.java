package org.observe.expresso.qonfig;

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
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.VariableType;
import org.observe.expresso.qonfig.ElementTypeTraceability.SingleTypeTraceability;
import org.observe.util.TypeTokens;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;

public interface ModelValueElement<M, MV extends M> extends ExElement {
	static final SingleTypeTraceability<ModelValueElement<?, ?>, Interpreted<?, ?, ?>, Def<?, ?>> TRACEABILITY = ElementTypeTraceability
		.getElementTraceability(ExpressoSessionImplV0_1.TOOLKIT_NAME, ExpressoSessionImplV0_1.VERSION, "model-value", Def.class,
			Interpreted.class, ModelValueElement.class);

	public interface Def<M, E extends ModelValueElement<M, ?>> extends ExElement.Def<E> {
		String getModelPath();

		ModelType<M> getModelType(CompiledExpressoEnv env);

		@Override
		@QonfigAttributeGetter
		CompiledExpression getElementValue();

		void populate(ObservableModelSet.Builder builder) throws QonfigInterpretationException;

		void prepareModelValue(ExpressoQIS session) throws QonfigInterpretationException;

		public abstract class Abstract<M, E extends ModelValueElement<M, ?>> extends ExElement.Def.Abstract<E> implements Def<M, E> {
			private final ModelType<M> theModelType;
			private CompiledExpression theValue;
			private String theModelPath;
			private boolean isPrepared;

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
				withTraceability(TRACEABILITY.validate(session.getFocusType(), session.reporting()));
				super.doUpdate(session);
				String name = getAddOnValue(ExNamed.Def.class, ExNamed.Def::getName);
				if (name != null) {
					theModelPath = session.get(ExpressoBaseV0_1.PATH_KEY, String.class);
					if (theModelPath == null)
						theModelPath = name;
					else
						theModelPath += "." + name;
				}
				theValue = session.getValueExpression();
			}

			@Override
			public final void prepareModelValue(ExpressoQIS session) throws QonfigInterpretationException {
				if (isPrepared)
					return;
				isPrepared = true;
				doPrepare(session);
			}

			protected abstract void doPrepare(ExpressoQIS session) throws QonfigInterpretationException;
		}

		public abstract class SingleTyped<M, E extends ModelValueElement<M, ?>> extends Abstract<M, E> {
			private VariableType theValueType;

			protected SingleTyped(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType, ModelType.SingleTyped<M> modelType) {
				super(parent, qonfigType, modelType);
			}

			@Override
			public ModelType.SingleTyped<M> getModelType(CompiledExpressoEnv env) {
				return (ModelType.SingleTyped<M>) super.getModelType(env);
			}

			public VariableType getValueType() {
				return theValueType;
			}

			@Override
			protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
				super.doUpdate(session);
				theValueType = session.get(ExpressoBaseV0_1.VALUE_TYPE_KEY, VariableType.class);
			}

			public static abstract class Interpreted<M, MV extends M, E extends ModelValueElement<M, MV>>
			extends ModelValueElement.Interpreted.Abstract<M, MV, E> {
				private ModelInstanceType<M, MV> theTargetType;

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
					if (getDefinition().getValueType() != null)
						theTargetType = (ModelInstanceType<M, MV>) getDefinition().getModelType(env)
						.forTypes(getDefinition().getValueType().getType(env));
					else
						theTargetType = getDefinition().getModelType(env).anyAs();
					super.doUpdate(env);
				}
			}
		}

		public abstract class DoubleTyped<M, E extends ModelValueElement<M, ?>> extends Abstract<M, E> {
			private VariableType theValueType1;
			private VariableType theValueType2;

			protected DoubleTyped(ExElement.Def<?> parent, QonfigElementOrAddOn element, ModelType.DoubleTyped<M> modelType) {
				super(parent, element, modelType);
			}

			@Override
			public ModelType.DoubleTyped<M> getModelType(CompiledExpressoEnv env) {
				return (ModelType.DoubleTyped<M>) super.getModelType(env);
			}

			public VariableType getValueType1() {
				return theValueType1;
			}

			public VariableType getValueType2() {
				return theValueType2;
			}

			@Override
			protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
				super.doUpdate(session);
				theValueType1 = session.get(ExpressoBaseV0_1.KEY_TYPE_KEY, VariableType.class);
				theValueType2 = session.get(ExpressoBaseV0_1.VALUE_TYPE_KEY, VariableType.class);
			}

			public static abstract class Interpreted<M, MV extends M, E extends ModelValueElement<M, MV>>
			extends ModelValueElement.Interpreted.Abstract<M, MV, E> {
				private ModelInstanceType<M, MV> theTargetType;

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
						if (getDefinition().getValueType2() != null)
							type = getDefinition().getModelType(env).forTypes(getDefinition().getValueType1().getType(env),
								getDefinition().getValueType2().getType(env));
						else
							type = getDefinition().getModelType(env).forTypes(getDefinition().getValueType1().getType(env),
								TypeTokens.get().WILDCARD);
					} else if (getDefinition().getValueType2() != null)
						type = getDefinition().getModelType(env).forTypes(TypeTokens.get().WILDCARD,
							getDefinition().getValueType2().getType(env));
					else
						type = getDefinition().getModelType(env).any();
					theTargetType = (ModelInstanceType<M, MV>) type;

					super.doUpdate(env);
				}
			}
		}
	}

	public interface Interpreted<M, MV extends M, E extends ModelValueElement<M, MV>> extends ExElement.Interpreted<E> {
		@Override
		Def<M, ? super E> getDefinition();

		ModelInstanceType<M, MV> getType();

		InterpretedValueSynth<?, ?> getElementValue();

		Interpreted<M, MV, E> setParentElement(ExElement.Interpreted<?> parent);

		void updateValue(InterpretedExpressoEnv env) throws ExpressoInterpretationException;

		E create(ExElement parent, ModelSetInstance models) throws ModelInstantiationException;

		public abstract class Abstract<M, MV extends M, E extends ModelValueElement<M, MV>> extends ExElement.Interpreted.Abstract<E>
		implements Interpreted<M, MV, E> {
			private InterpretedValueSynth<M, MV> theValue;

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

			@Override
			public Interpreted.Abstract<M, MV, E> setParentElement(ExElement.Interpreted<?> parent) {
				super.setParentElement(parent);
				return this;
			}

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
					theValue = getDefinition().getElementValue().interpret(getTargetType(), getExpressoEnv());
			}

			@Override
			public E create(ExElement parent, ModelSetInstance models) throws ModelInstantiationException {
				return (E) new ModelValueElement.Default<>(this, parent);
			}
		}
	}

	Object getElementValue();

	public class Default<M, MV extends M> extends ExElement.Abstract implements ModelValueElement<M, MV> {
		private Object theElementValue;

		public Default(ModelValueElement.Interpreted<M, MV, ?> interpreted, ExElement parent) {
			super(interpreted, parent);
		}

		@Override
		public Object getElementValue() {
			return theElementValue;
		}

		@Override
		protected void updateModel(ExElement.Interpreted<?> interpreted, ModelSetInstance myModels) throws ModelInstantiationException {
			super.updateModel(interpreted, myModels);
			ModelValueElement.Interpreted<M, MV, ?> myInterpreted = (ModelValueElement.Interpreted<M, MV, ?>) interpreted;
			theElementValue = updateValue(myInterpreted, myModels);
		}

		protected Object updateValue(ModelValueElement.Interpreted<M, MV, ?> myInterpreted, ModelSetInstance myModels)
			throws ModelInstantiationException {
			return myInterpreted.getElementValue() == null ? null : myInterpreted.getElementValue().get(myModels);
		}
	}

	public interface CompiledSynth<M, E extends ModelValueElement<M, ?>> extends ModelValueElement.Def<M, E>, CompiledModelValue<M> {
		@Override
		default void populate(ObservableModelSet.Builder builder) throws QonfigInterpretationException {
			String name = getAddOnValue(ExNamed.Def.class, ExNamed.Def::getName);
			if (name == null)
				throw new QonfigInterpretationException("Not named, cannot add to model set", getElement().getPositionInFile(), 0);
			builder.withMaker(name, this, reporting().getFileLocation().getPosition(0));
		}

		@Override
		default InterpretedSynth<M, ?, ? extends E> interpret(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
			InterpretedSynth<M, ?, ? extends E> interpreted = interpret();
			interpreted.updateValue(env);
			return interpreted;
		}

		InterpretedSynth<M, ?, ? extends E> interpret();
	}

	public interface InterpretedSynth<M, MV extends M, E extends ModelValueElement<M, MV>>
	extends ModelValueElement.Interpreted<M, MV, E>, InterpretedValueSynth<M, MV> {
		@Override
		InterpretedSynth<M, MV, E> setParentElement(ExElement.Interpreted<?> parent);
	}
}
