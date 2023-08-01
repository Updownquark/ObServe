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
import org.observe.expresso.qonfig.ElementTypeTraceability.SingleTypeTraceability;
import org.observe.util.TypeTokens;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.io.LocatedFilePosition;

import com.google.common.reflect.TypeToken;

public class ExtModelValueElement<M, MV extends M> extends ModelValueElement.Default<M, MV> {
	private static final SingleTypeTraceability<ExtModelValueElement<?, ?>, Interpreted<?, ?>, Def<?>> TRACEABILITY = ElementTypeTraceability
		.getElementTraceability(ExpressoSessionImplV0_1.TOOLKIT_NAME, ExpressoSessionImplV0_1.VERSION, "ext-model-value", Def.class,
			Interpreted.class, ExtModelValueElement.class);

	public static abstract class Def<M> extends ModelValueElement.Def.Abstract<M, ExtModelValueElement<M, ?>> implements ExtValueRef<M> {
		private CompiledExpression theDefault;

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
		public void populate(ObservableModelSet.Builder builder) throws QonfigInterpretationException {
			String name = getAddOnValue(ExNamed.Def.class, ExNamed.Def::getName);
			if (name == null)
				throw new QonfigInterpretationException("Not named, cannot add to model set", getElement().getPositionInFile(), 0);
			builder.withExternal(name, this, reporting().getFileLocation().getPosition(0));
		}

		@QonfigAttributeGetter("default")
		public CompiledExpression getDefault() {
			return theDefault;
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			// This can be used with element-model values as well
			boolean isExtValue = session.isInstance("ext-model-value") != null;
			if (isExtValue)
				withTraceability(TRACEABILITY.validate(session.getFocusType(), session.reporting()));
			super.doUpdate(session.asElement(session.getFocusType().getSuperElement()));

			if (isExtValue)
				theDefault = session.getAttributeExpression("default");
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
				M value = env.getExtModels().getValue(path, type);
				return InterpretedValueSynth.of((ModelInstanceType<M, M>) type, __ -> value);
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

		public static class Single<M> extends ExtModelValueElement.Def<M> {
			private final String theTypeName;

			public Single(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType, ModelType.SingleTyped<M> modelType, String typeName) {
				super(parent, qonfigType, modelType);
				theTypeName = typeName;
			}

			@Override
			public ModelType.SingleTyped<M> getModelType() {
				return (ModelType.SingleTyped<M>) super.getModelType();
			}

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

		public static class Double<M> extends Def<M> {
			private final String theTypeName;

			public Double(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType, ModelType.DoubleTyped<M> modelType, String typeName) {
				super(parent, qonfigType, modelType);
				theTypeName = typeName;
			}

			@Override
			public ModelType.DoubleTyped<M> getModelType() {
				return (ModelType.DoubleTyped<M>) super.getModelType();
			}

			public VariableType getKeyType() {
				return getAddOnValue(ExMapModelValue.Def.class, ExMapModelValue.Def::getKeyType);
			}

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

	public static abstract class Interpreted<M, MV extends M>
	extends ModelValueElement.Interpreted.Abstract<M, MV, ExtModelValueElement<M, MV>> {
		private InterpretedValueSynth<M, MV> theDefault;

		protected Interpreted(Def<M> definition, ExElement.Interpreted<?> parent) throws ExpressoInterpretationException {
			super(definition, parent);
		}

		@Override
		public Def<M> getDefinition() {
			return (Def<M>) super.getDefinition();
		}

		public InterpretedValueSynth<M, MV> getDefault() {
			return theDefault;
		}

		@Override
		protected void doUpdate(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
			super.doUpdate(env);

			theDefault = getDefinition().getDefault() == null ? null : getDefinition().getDefault().interpret(getType(), getExpressoEnv());
		}
	}

	private MV theDefault;

	public ExtModelValueElement(Interpreted<M, MV> interpreted, ExElement parent) {
		super(interpreted, parent);
	}

	public MV getDefault() {
		return theDefault;
	}

	@Override
	protected void updateModel(ExElement.Interpreted<?> interpreted, ModelSetInstance myModels) throws ModelInstantiationException {
		super.updateModel(interpreted, myModels);
		Interpreted<M, MV> myInterpreted = (Interpreted<M, MV>) interpreted;
		theDefault = myInterpreted.getDefault() == null ? null : myInterpreted.getDefault().get(myModels);
	}
}
