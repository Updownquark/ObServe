package org.observe.expresso.qonfig;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.observe.expresso.CompiledExpressoEnv;
import org.observe.expresso.ExpressoCompilationException;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.InterpretedExpressoEnv;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelType;
import org.observe.expresso.ModelType.HollowModelValue;
import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.expresso.ObservableModelSet;
import org.observe.expresso.ObservableModelSet.CompiledModelValue;
import org.observe.expresso.ObservableModelSet.InterpretableModelComponentNode;
import org.observe.expresso.ObservableModelSet.InterpretedModelComponentNode;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelComponentId;
import org.observe.expresso.ObservableModelSet.ModelComponentNode;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ModelValueInstantiator;
import org.observe.util.TypeTokens;
import org.qommons.config.QonfigAddOn;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.ex.ExBiFunction;
import org.qommons.io.LocatedFilePosition;

import com.google.common.reflect.TypeToken;

public abstract class ExFlexibleElementModelAddOn<E extends ExElement> extends ExModelAugmentation<E> {
	public static void satisfyElementValue(ModelComponentId elementValueId, ModelSetInstance models, Object value)
		throws ModelInstantiationException {
		satisfyElementValue(elementValueId, models, value, ActionIfSatisfied.Error);
	}

	public static void satisfyElementValue(ModelComponentId elementValueId, ModelSetInstance models, Object value,
		ActionIfSatisfied ifPreSatisfied) throws ModelInstantiationException {
		Object modelValue = models.get(elementValueId);
		if (!(modelValue instanceof ModelType.HollowModelValue))
			throw new IllegalArgumentException("Element value '" + elementValueId + "' is not dynamic");
		ModelType.HollowModelValue<Object, Object> hollow = (ModelType.HollowModelValue<Object, Object>) modelValue;
		if (hollow.isSatisfied()) {
			switch (ifPreSatisfied) {
			case Error:
				throw new IllegalArgumentException("Element value '" + elementValueId + "' is already satisfied");
			case Ignore:
				return;
			case Replace:
				break;
			}
		}
		hollow.satisfy(value);
	}

	public static abstract class Def<E extends ExElement, AO extends ExFlexibleElementModelAddOn<? super E>>
	extends ExModelAugmentation.Def<E, AO> {
		private Map<String, CompiledModelValue<?>> theElementValues;
		private volatile Interpreted<? extends E, ? extends AO> theCurrentInterpreting;

		protected Def(QonfigAddOn type, ExElement.Def<? extends E> element) {
			super(type, element);
			theElementValues = Collections.emptyMap();
		}

		@Override
		public void update(ExpressoQIS session, ExElement.Def<? extends E> element) throws QonfigInterpretationException {
			if (!theElementValues.isEmpty())
				theElementValues.clear();
			super.update(session, element);
		}

		public Map<String, ? extends CompiledModelValue<?>> getElementValues() {
			return Collections.unmodifiableMap(theElementValues);
		}

		protected <M> ModelComponentNode<M> addElementValue(String name, CompiledModelValue<M> value, ObservableModelSet.Builder builder,
			LocatedFilePosition position) throws QonfigInterpretationException {
			try {
				builder.getNameChecker().checkName(name);
			} catch (IllegalArgumentException e) {
				throw new QonfigInterpretationException("Illegal variable name: '" + name + "'", position, 0, e);
			}
			if (theElementValues.isEmpty())
				theElementValues = new LinkedHashMap<>();
			CompiledModelValue<?> prev = getElementValues().get(name);
			if (prev != null)
				throw new QonfigInterpretationException("Multiple conflicting element values named '" + name + "' declared", position, 0);
			theElementValues.put(name, value);
			builder.withMaker(name, value, position);
			return (ModelComponentNode<M>) builder.getLocalComponent(name);
		}

		protected CompiledModelValue<?> getElementValue(String elementValueName) throws QonfigInterpretationException {
			CompiledModelValue<?> value = theElementValues.get(elementValueName);
			if (value == null)
				throw new QonfigInterpretationException("No such element value '" + elementValueName + "'",
					getElement().reporting().getPosition(), 0);
			return value;
		}

		protected <I extends ExElement.Interpreted<?>, M> void satisfyElementValueType(ModelComponentId elementValueId,
			ModelType<M> modelType,
			ExBiFunction<I, InterpretedExpressoEnv, ? extends ModelInstanceType<M, ?>, ExpressoInterpretationException> type)
				throws QonfigInterpretationException {
			ModelComponentNode<?> value = getElement().getExpressoEnv().getModels().getComponent(elementValueId);
			if (!(value.getThing() instanceof PlaceholderModelValue))
				throw new QonfigInterpretationException("Element value '" + elementValueId + "' is not dynamically-typed",
					getElement().reporting().getPosition(), 0);
			try {
				if (value.getModelType(null) != modelType)
					throw new QonfigInterpretationException(
						"Element value '" + elementValueId + "' is not a " + value.getModelType(null) + ", not a " + modelType,
						getElement().reporting().getPosition(), 0);
			} catch (ExpressoCompilationException e) {
				throw new QonfigInterpretationException(e.getMessage(), e.getPosition(), e.getErrorLength(), e);
			}
			((PlaceholderModelValue<M>) value.getThing()).satisfyType(this::getCurrentInterpreting,
				(ExBiFunction<ExElement.Interpreted<?>, InterpretedExpressoEnv, ? extends ModelInstanceType<M, ?>, ExpressoInterpretationException>) type);
		}

		protected <I extends ExElement.Interpreted<?>, M> void satisfyElementValueType(ModelComponentId elementValueId,
			ModelInstanceType<M, ?> type) throws QonfigInterpretationException {
			satisfyElementValueType(elementValueId, type.getModelType(), (interp, env) -> type);
		}

		@Override
		public abstract Interpreted<? extends E, ? extends AO> interpret(ExElement.Interpreted<? extends E> element);

		void setCurrentInterpreting(Interpreted<? extends E, ? extends AO> interpreting) {
			theCurrentInterpreting = interpreting;
		}

		Interpreted<? extends E, ? extends AO> getCurrentInterpreting() {
			return theCurrentInterpreting;
		}
	}

	public static abstract class Interpreted<E extends ExElement, AO extends ExFlexibleElementModelAddOn<? super E>>
	extends ExAddOn.Interpreted.Abstract<E, AO> {
		private final Map<String, InterpretedModelComponentNode<?, ?>> theElementValues;

		protected Interpreted(Def<E, ? super AO> definition, ExElement.Interpreted<? extends E> element) {
			super(definition, element);
			if (definition.getElementValues().isEmpty())
				theElementValues = Collections.emptyMap();
			else
				theElementValues = new LinkedHashMap<>();
		}

		@Override
		public Def<E, ? super AO> getDefinition() {
			return (Def<E, ? super AO>) super.getDefinition();
		}

		public Map<String, InterpretedModelComponentNode<?, ?>> getElementValues() {
			return Collections.unmodifiableMap(theElementValues);
		}

		@Override
		public void preUpdate() {
			getDefinition().setCurrentInterpreting(this);
		}

		@Override
		public void postUpdate() throws ExpressoInterpretationException {
			for (Map.Entry<String, ? extends CompiledModelValue<?>> elementValue : getDefinition().getElementValues().entrySet()) {
				if (theElementValues.containsKey(elementValue.getKey()))
					continue;
				InterpretableModelComponentNode<?> node = getElement().getExpressoEnv().getModels()
					.getComponentIfExists(elementValue.getKey(), false);
				theElementValues.put(elementValue.getKey(), node.interpreted());
			}
			getDefinition().setCurrentInterpreting(null);
		}

		protected InterpretedModelComponentNode<?, ?> getElementValue(String elementValueName) throws ExpressoInterpretationException {
			InterpretedModelComponentNode<?, ?> value = theElementValues.get(elementValueName);
			if (value == null) {
				CompiledModelValue<?> defValue = getDefinition().getElementValues().get(elementValueName);
				if (defValue == null)
					throw new ExpressoInterpretationException("No such element value '" + elementValueName + "'",
						getElement().reporting().getPosition(), 0);
				InterpretableModelComponentNode<?> node = getElement().getExpressoEnv().getModels().getComponentIfExists(elementValueName,
					false);
				value = node.interpreted();
				theElementValues.put(elementValueName, value);
			}
			return value;
		}

		protected <M, MV extends M> void satisfyElementValue(String elementValueName, InterpretedValueSynth<M, MV> satisfier)
			throws ExpressoInterpretationException {
			InterpretedModelComponentNode<?, ?> value = getElementValue(elementValueName);
			if (!(value.getValue() instanceof PlaceholderModelValue.Interpreted))
				throw new ExpressoInterpretationException("Element value '" + elementValueName + "' is not dynamic",
					getElement().reporting().getPosition(), 0);
			((PlaceholderModelValue.Interpreted<M, MV>) value.getValue()).satisfy(satisfier);
		}

		protected void satisfyElementValue(String elementValueName, ModelSetInstance models, Object value)
			throws ModelInstantiationException {
			satisfyElementValue(elementValueName, models, value, ActionIfSatisfied.Error);
		}

		protected void satisfyElementValue(String elementValueName, ModelSetInstance models, Object value, ActionIfSatisfied ifPreSatisfied)
			throws ModelInstantiationException {
			InterpretedModelComponentNode<?, ?> elementValue = theElementValues.get(elementValueName);
			if (elementValue == null)
				throw new ModelInstantiationException("No such element value '" + elementValueName + "'",
					getElement().reporting().getPosition(), 0);
			else if (!(elementValue.getValue() instanceof PlaceholderModelValue.Interpreted))
				throw new ModelInstantiationException("Element value '" + elementValueName + "' is not dynamic",
					getElement().reporting().getPosition(), 0);
			Object modelValue = models.get(elementValue.getIdentity());
			if (!(modelValue instanceof ModelType.HollowModelValue))
				throw new ModelInstantiationException("Element value '" + elementValueName + "' is not dynamic",
					getElement().reporting().getPosition(), 0);
			ModelType.HollowModelValue<Object, Object> hollow = (ModelType.HollowModelValue<Object, Object>) modelValue;
			if (hollow.isSatisfied()) {
				switch (ifPreSatisfied) {
				case Error:
					throw new ModelInstantiationException("Element value '" + elementValueName + "' is already satisfied",
						getElement().reporting().getPosition(), 0);
				case Ignore:
					return;
				case Replace:
					break;
				}
			}
			hollow.satisfy(value);
		}
	}

	public enum ActionIfSatisfied {
		Error, Ignore, Replace
	}

	protected ExFlexibleElementModelAddOn(E element) {
		super(element);
	}

	protected static abstract class PlaceholderModelValue<M> implements CompiledModelValue<M> {
		private final String theName;
		private Supplier<ExFlexibleElementModelAddOn.Interpreted<?, ?>> theInterpreting;
		private ExBiFunction<ExElement.Interpreted<?>, InterpretedExpressoEnv, ? extends ModelInstanceType<M, ?>, ExpressoInterpretationException> theType;

		protected PlaceholderModelValue(String name) {
			theName = name;
		}

		public abstract ModelType<M> getModelType();

		@Override
		public ModelType<M> getModelType(CompiledExpressoEnv env) {
			return getModelType();
		}

		void satisfyType(Supplier<ExFlexibleElementModelAddOn.Interpreted<?, ?>> interpreting,
			ExBiFunction<ExElement.Interpreted<?>, InterpretedExpressoEnv, ? extends ModelInstanceType<M, ?>, ExpressoInterpretationException> type) {
			theInterpreting = interpreting;
			theType = type;
		}

		protected <MV extends M> InterpretedValueSynth<M, MV> create(InterpretedExpressoEnv env, ModelInstanceType<M, MV> defaultType)
			throws ExpressoInterpretationException {
			ModelInstanceType<M, MV> type;
			if (theType != null) {
				ModelInstanceType<?, ?> targetType = theType.apply(theInterpreting.get().getElement(), env);
				for (int t = 0; t < getModelType().getTypeCount(); t++) {
					TypeToken<?> pt = targetType.getType(t);
					if (!TypeTokens.get().isAssignable(defaultType.getType(t), pt))
						throw new ExpressoInterpretationException(
							"Dynamic model value '" + this + "' (" + defaultType + ") satisfied with " + targetType,
							env.reporting().getPosition(), 0);
				}
				type = (ModelInstanceType<M, MV>) targetType;
			} else {
				boolean wildcard = false;
				for (int t = 0; !wildcard && t < getModelType().getTypeCount(); t++)
					wildcard = TypeTokens.get().isTrivialType(defaultType.getType(t).getType());
				if (wildcard)
					throw new ExpressoInterpretationException(
						"Type not specified for dynamic model value '" + this + "' (" + defaultType + ") before being needed",
						env.reporting().getPosition(), 0);
				type = defaultType;
			}
			return interpret(type);
		}

		protected <MV extends M> Interpreted<M, MV> interpret(ModelInstanceType<M, MV> type) throws ExpressoInterpretationException {
			return new Interpreted<>(theName, type);
		}

		@Override
		public String toString() {
			return theName;
		}

		protected static class Interpreted<M, MV extends M> implements InterpretedValueSynth<M, MV> {
			private final String theName;
			private final ModelInstanceType<M, MV> theType;
			private InterpretedValueSynth<M, MV> theSatisfier;

			protected Interpreted(String name, ModelInstanceType<M, MV> type) {
				theName = name;
				theType = type;
			}

			void satisfy(InterpretedValueSynth<M, MV> satisfier) {
				ModelInstanceType<M, MV> satisfierType = satisfier.getType();
				if (satisfierType.getModelType() != theType.getModelType()) {
					throw new IllegalStateException("Dynamic model value '" + this + "' (" + theType + ") satisfied with " + satisfierType);
				} else {
					for (int t = 0; t < theType.getModelType().getTypeCount(); t++) {
						TypeToken<?> pt = satisfierType.getType(t);
						if (!TypeTokens.get().isAssignable(theType.getType(t), pt))
							throw new IllegalStateException(
								"Dynamic model value '" + this + "' (" + theType + ") satisfied with " + satisfierType);
					}
				}
				theSatisfier = satisfier;
			}

			@Override
			public ModelType<M> getModelType() {
				return theType.getModelType();
			}

			@Override
			public ModelInstanceType<M, MV> getType() {
				if (theSatisfier != null)
					return theSatisfier.getType();
				else
					return theType;
			}

			@Override
			public List<? extends InterpretedValueSynth<?, ?>> getComponents() {
				if (theSatisfier != null)
					return Collections.singletonList(theSatisfier);
				else
					return Collections.emptyList();
			}

			@Override
			public ModelValueInstantiator<MV> instantiate() {
				if (theSatisfier != null)
					return theSatisfier.instantiate();
				else
					return new HollowValueInstantiator<>(theName, theType);
			}

			@Override
			public String toString() {
				return theName;
			}
		}

		protected static class HollowValueInstantiator<M, MV extends M> implements ModelValueInstantiator<MV> {
			private final String theName;
			private final ModelInstanceType<M, MV> theType;

			protected HollowValueInstantiator(String name, ModelInstanceType<M, MV> type) {
				theName = name;
				theType = type;
			}

			@Override
			public void instantiate() {
			}

			@Override
			public MV get(ModelSetInstance models) throws ModelInstantiationException, IllegalStateException {
				return (MV) theType.getModelType().createHollowValue(theName, theType);
			}

			@Override
			public MV forModelCopy(MV value, ModelSetInstance sourceModels, ModelSetInstance newModels) throws ModelInstantiationException {
				HollowModelValue<M, MV> hollow = theType.getModelType().createHollowValue(theName, theType);
				hollow.satisfy(value);
				return (MV) hollow;
			}
		}
	}
}
