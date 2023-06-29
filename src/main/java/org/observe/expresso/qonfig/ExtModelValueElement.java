package org.observe.expresso.qonfig;

import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelType;
import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.expresso.ObservableModelSet;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.VariableType;
import org.observe.util.TypeTokens;
import org.qommons.Version;
import org.qommons.config.QonfigElement;
import org.qommons.config.QonfigInterpretationException;

import com.google.common.reflect.TypeToken;

public class ExtModelValueElement<M, MV extends M> extends ModelValueElement.Default<M, MV> {
	private static final ExElement.AttributeValueGetter.Expression<ExtModelValueElement<?, ?>, Interpreted<?, ?>, Def<?>, Object, Object> DEFAULT = ExElement.AttributeValueGetter
		.<ExtModelValueElement<?, ?>, Interpreted<?, ?>, Def<?>, Object, Object> ofX(Def::getDefault,
			i -> (InterpretedValueSynth<Object, Object>) i.getDefault(), ExtModelValueElement::getDefault);

	public static abstract class Def<M> extends ModelValueElement.Def.Simple<M, ExtModelValueElement<M, ?>> implements ExtModelValue<M> {
		private CompiledExpression theDefault;

		protected Def(ExElement.Def<?> parent, QonfigElement element, ModelType<M> modelType, String toolkitName, Version toolkitVersion,
			String typeName) {
			super(parent, element, modelType, toolkitName, toolkitVersion, typeName);
		}

		public CompiledExpression getDefault() {
			return theDefault;
		}

		@Override
		public void update(ExpressoQIS session) throws QonfigInterpretationException {
			forAttribute(session.getAttributeDef(null, null, "default"), DEFAULT);
			super.update(session);

			theDefault = session.getAttributeExpression("default");
		}

		public static class Single<M> extends Def<M> {
			private final VariableType theValueType;

			public Single(ExElement.Def<?> parent, QonfigElement element, ModelType.SingleTyped<M> modelType, VariableType valueType,
				String toolkitName, Version toolkitVersion, String typeName) {
				super(parent, element, modelType, toolkitName, toolkitVersion, typeName);
				theValueType = valueType;
			}

			@Override
			public ModelType.SingleTyped<M> getModelType() {
				return (ModelType.SingleTyped<M>) super.getModelType();
			}

			@Override
			public boolean isTypeSpecified() {
				return theValueType != null;
			}

			@Override
			public ModelInstanceType<M, ?> getType(ObservableModelSet models) throws ExpressoInterpretationException {
				if (theValueType != null)
					return getModelType().forType(theValueType.getType(models));
				else
					return getModelType().any();
			}

			public VariableType getValueType() {
				return theValueType;
			}
		}

		public static class Double<M> extends Def<M> {
			private final VariableType theValueType1;
			private final VariableType theValueType2;

			public Double(ExElement.Def<?> parent, QonfigElement element, ModelType.DoubleTyped<M> modelType, VariableType valueType1,
				VariableType valueType2, String toolkitName, Version toolkitVersion, String typeName) {
				super(parent, element, modelType, toolkitName, toolkitVersion, typeName);
				theValueType1 = valueType1;
				theValueType2 = valueType2;
			}

			@Override
			public ModelType.DoubleTyped<M> getModelType() {
				return (ModelType.DoubleTyped<M>) super.getModelType();
			}

			@Override
			public boolean isTypeSpecified() {
				return theValueType1 != null;
			}

			@Override
			public ModelInstanceType<M, ?> getType(ObservableModelSet models) throws ExpressoInterpretationException {
				if (theValueType1 != null && theValueType2 != null)
					return getModelType().forType(//
						(TypeToken<?>) theValueType1.getType(models), //
						(TypeToken<?>) theValueType2.getType(models)//
						);
				else if (theValueType1 == null && theValueType2 == null)
					return getModelType().any();
				else
					return getModelType().forType(//
						(TypeToken<?>) (theValueType1 == null ? TypeTokens.get().WILDCARD : theValueType1.getType(models)), //
						(TypeToken<?>) (theValueType2 == null ? TypeTokens.get().WILDCARD : theValueType2.getType(models))//
						);
			}

			public VariableType getValueType1() {
				return theValueType1;
			}

			public VariableType getValueType2() {
				return theValueType2;
			}
		}
	}

	public static abstract class Interpreted<M, MV extends M>
	extends ModelValueElement.Interpreted.Default<M, MV, ExtModelValueElement<M, MV>> {
		private InterpretedValueSynth<M, MV> theDefault;

		protected Interpreted(Def<M> definition, ExElement.Interpreted<?> parent, ModelInstanceType<M, MV> valueType)
			throws ExpressoInterpretationException {
			super(definition, parent, valueType);
		}

		@Override
		public Def<M> getDefinition() {
			return (Def<M>) super.getDefinition();
		}

		public InterpretedValueSynth<M, MV> getDefault() {
			return theDefault;
		}

		@Override
		public void update() throws ExpressoInterpretationException {
			super.update();

			theDefault = getDefinition().getDefault() == null ? null : getDefinition().getDefault().evaluate(getValueType()).interpret();
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
