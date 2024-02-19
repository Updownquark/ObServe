package org.observe.expresso.qonfig;

import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.InterpretedExpressoEnv;
import org.observe.expresso.ModelException;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelType;
import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.expresso.ObservableModelSet;
import org.observe.expresso.ObservableModelSet.ExtValueRef;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.TypeConversionException;
import org.observe.expresso.VariableType;
import org.observe.util.TypeTokens;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.config.SessionValues;
import org.qommons.io.LocatedFilePosition;

import com.google.common.reflect.TypeToken;

/**
 * An element defining a model value that must be specified from outside the scope of its document
 *
 * @param <MV> The type of the model value
 */
public class ExtModelValueElement<MV> extends ModelValueElement.Abstract<MV> {
	/** The XML name of this element */
	public static final String EXT_MODEL_VALUE = "ext-model-value";
	/**
	 * The session key in the {@link ExpressoQIS} to {@link SessionValues#put(String, Object) put} a {@link ExtModelValueHandler handler} to
	 * handle external model values
	 */
	public static final String EXT_MODEL_VALUE_HANDLER = "Ext.Model.Value.Handler";

	/** A handler for external model values */
	public interface ExtModelValueHandler {
		/**
		 * @param <M> The model type of the value
		 * @param value The definition of the value
		 * @param builder The model builder to populate with the external value
		 * @param valueSession The expresso session for the model value
		 * @throws QonfigInterpretationException If the external value could not be interpreted
		 */
		<M> void handleExtValue(Def<M> value, ObservableModelSet.Builder builder, ExpressoQIS valueSession)
			throws QonfigInterpretationException;
	}

	/**
	 * External model value element definition
	 *
	 * @param <M> The model type of the value
	 */
	@ExElementTraceable(toolkit = ExpressoSessionImplV0_1.CORE, qonfigType = EXT_MODEL_VALUE, interpretation = Interpreted.class)
	public static abstract class Def<M> extends ModelValueElement.Def.Abstract<M, ExtModelValueElement<?>> implements ExtValueRef<M> {
		private CompiledExpression theDefault;

		/**
		 * @param parent The parent element defining this external model value
		 * @param qonfigType The Qonfig type of this element
		 * @param modelType The model type of the value
		 */
		protected Def(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType, ModelType<M> modelType) {
			super(parent, qonfigType, modelType);
		}

		@Override
		public ModelType<M> getModelType() {
			return getModelType(null);
		}

		@Override
		public void setParentElement(ExElement.Def<?> parent) {
			super.setParentElement(parent);
		}

		@Override
		public void populate(ObservableModelSet.Builder builder, ExpressoQIS session) throws QonfigInterpretationException {
			String name = getAddOnValue(ExNamed.Def.class, ExNamed.Def::getName);
			if (name == null)
				throw new QonfigInterpretationException("Not named, cannot add to model set", getElement().getPositionInFile(), 0);
			ExtModelValueHandler handler = session.get(EXT_MODEL_VALUE_HANDLER, ExtModelValueHandler.class);
			if (handler != null)
				handler.handleExtValue(this, builder, session);
			else
				builder.withExternal(name, this, reporting().getFileLocation().getPosition(0));
		}

		/** @return The expression defining the value to use for the value if it is not supplied externally */
		@QonfigAttributeGetter("default")
		public CompiledExpression getDefault() {
			return theDefault;
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			// This can be used with element-model values as well
			boolean isExtValue = session.isInstance(EXT_MODEL_VALUE) != null;
			super.doUpdate(session.asElement(session.getFocusType().getSuperElement()));

			if (isExtValue)
				theDefault = getAttributeExpression("default", session);
		}

		@Override
		protected void doPrepare(ExpressoQIS session) { // Nothing to do in general
		}

		@Override
		public InterpretedValueSynth<M, ?> createSynthesizer(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
			ModelInstanceType<M, ?> type = getType(env);
			try {
				String path = getModelPath();
				// Model path includes the root
				path = path.substring(path.indexOf('.') + 1);
				M value = env.getExtModels().getValue(path, type, env);
				return InterpretedValueSynth.literal((ModelInstanceType<M, M>) type, value, getModelPath());
			} catch (ModelException e) {
				// No such external model. Use the default if present
				if (theDefault != null)
					return theDefault.interpret(type, env);
				else
					throw new ExpressoInterpretationException(e.getMessage(), getFilePosition(), 0, e);
			} catch (TypeConversionException e) {
				throw new ExpressoInterpretationException(e.getMessage(), getFilePosition(), 0, e);
			} catch (ModelInstantiationException e) {
				throw new ExpressoInterpretationException(e.getMessage(), e.getPosition(), e.getErrorLength(), e);
			}
		}

		@Override
		public boolean hasDefault() {
			return theDefault != null;
		}

		@Override
		public LocatedFilePosition getFilePosition() {
			return getElement().getPositionInFile();
		}

		/**
		 * A {@link Def definition} for an {@link ExtModelValueElement} that has no type parameters
		 *
		 * @param <M> The model type of the value
		 */
		public static class UnTyped<M> extends Def<M> {
			/**
			 * @param parent The parent element defining this external model value
			 * @param qonfigType The Qonfig type of this element
			 * @param modelType The model type of the value
			 */
			public UnTyped(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType, ModelType.UnTyped<M> modelType) {
				super(parent, qonfigType, modelType);
			}

			@Override
			public ModelType.UnTyped<M> getModelType() {
				return (ModelType.UnTyped<M>) super.getModelType();
			}

			@Override
			public ModelInstanceType<M, ?> getType(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
				return getModelType().instance();
			}
		}

		/**
		 * A {@link Def definition} for an {@link ExtModelValueElement} that has one type parameter
		 *
		 * @param <M> The model type of the value
		 */
		public static class Single<M> extends Def<M> {
			private final String theTypeName;

			/**
			 * @param parent The parent element defining this external model value
			 * @param qonfigType The Qonfig type of this element
			 * @param modelType The model type of the value
			 * @param typeName The name of the model value's type
			 */
			public Single(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType, ModelType.SingleTyped<M> modelType, String typeName) {
				super(parent, qonfigType, modelType);
				theTypeName = typeName;
			}

			@Override
			public ModelType.SingleTyped<M> getModelType() {
				return (ModelType.SingleTyped<M>) super.getModelType();
			}

			/** @return The declared type of the model value */
			public VariableType getValueType() {
				return getAddOnValue(ExTyped.Def.class, ExTyped.Def::getValueType);
			}

			@Override
			public ModelInstanceType<M, ?> getType(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
				VariableType valueType = getValueType();
				if (valueType != null)
					return getModelType().forType(valueType.getType(env));
				else
					return getModelType().any();
			}

			@Override
			protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
				if (!session.getFocusType().getName().equals(theTypeName))
					throw new IllegalStateException("Expected '" + theTypeName + "', not '" + session.getFocusType().getName() + "'");
				QonfigElementOrAddOn extModelValue = session.isInstance("ext-model-value");
				super.doUpdate(extModelValue == null ? session : session.asElement(extModelValue));
			}
		}

		/**
		 * A {@link Def definition} for an {@link ExtModelValueElement} that has two type parameters
		 *
		 * @param <M> The model type of the value
		 */
		public static class Double<M> extends Def<M> {
			private final String theTypeName;

			/**
			 * @param parent The parent element defining this external model value
			 * @param qonfigType The Qonfig type of this element
			 * @param modelType The model type of the value
			 * @param typeName The name of the model value's type
			 */
			public Double(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType, ModelType.DoubleTyped<M> modelType, String typeName) {
				super(parent, qonfigType, modelType);
				theTypeName = typeName;
			}

			@Override
			public ModelType.DoubleTyped<M> getModelType() {
				return (ModelType.DoubleTyped<M>) super.getModelType();
			}

			/** @return The declared key type of the model value */
			public VariableType getKeyType() {
				return getAddOnValue(ExMapModelValue.Def.class, ExMapModelValue.Def::getKeyType);
			}

			/** @return The declared value type of the model value */
			public VariableType getValueType() {
				return getAddOnValue(ExTyped.Def.class, ExTyped.Def::getValueType);
			}

			@Override
			public ModelInstanceType<M, ?> getType(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
				VariableType keyType = getKeyType();
				VariableType valueType = getValueType();
				if (keyType != null && valueType != null)
					return getModelType().forType(//
						(TypeToken<?>) keyType.getType(env), //
						(TypeToken<?>) valueType.getType(env)//
						);
				else if (keyType == null && valueType == null)
					return getModelType().any();
				else
					return getModelType().forType(//
						(TypeToken<?>) (keyType == null ? TypeTokens.get().WILDCARD : keyType.getType(env)), //
						(TypeToken<?>) (valueType == null ? TypeTokens.get().WILDCARD : valueType.getType(env))//
						);
			}

			@Override
			protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
				if (!session.getFocusType().getName().equals(theTypeName))
					throw new IllegalStateException("Expected '" + theTypeName + "', not '" + session.getFocusType().getName() + "'");
				super.doUpdate(session.asElement("ext-model-value"));
			}
		}
	}

	/**
	 * External model value element interpretation
	 *
	 * @param <M> The model type of the external value
	 * @param <MV> The instance type of the external value
	 */
	public static abstract class Interpreted<M, MV extends M>
	extends ModelValueElement.Interpreted.Abstract<M, MV, ExtModelValueElement<MV>> {
		private InterpretedValueSynth<M, MV> theDefault;

		/**
		 * @param definition The definition to interpret
		 * @param parent The parent element defining this external value
		 */
		protected Interpreted(Def<M> definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
		}

		@Override
		public Def<M> getDefinition() {
			return (Def<M>) super.getDefinition();
		}

		/** @return The expression defining the value to use for the value if it is not supplied externally */
		public InterpretedValueSynth<M, MV> getDefault() {
			return theDefault;
		}

		@Override
		protected void doUpdate(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
			super.doUpdate(env);

			theDefault = interpret(getDefinition().getDefault(), getType());
		}
	}

	private ExtModelValueElement() throws ModelInstantiationException {
		super(null);
	}

	@Override
	public void instantiate() {
	}

	@Override
	public MV get(ModelSetInstance models) throws ModelInstantiationException, IllegalStateException {
		return null;
	}

	@Override
	public MV forModelCopy(MV value, ModelSetInstance sourceModels, ModelSetInstance newModels) throws ModelInstantiationException {
		return null;
	}
}
