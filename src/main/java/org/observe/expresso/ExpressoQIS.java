package org.observe.expresso;

import java.util.function.Function;
import java.util.function.Supplier;

import org.observe.SettableValue;
import org.observe.collect.ObservableCollection;
import org.observe.expresso.Expression.ExpressoParseException;
import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ValueContainer;
import org.observe.util.TypeTokens;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.config.QonfigInterpreterCore.CoreSession;
import org.qommons.config.SpecialSession;

import com.google.common.reflect.TypeToken;

public class ExpressoQIS implements SpecialSession<ExpressoQIS> {
	public static final String PARENT_MODEL_NAME = "__PARENT$MODEL$INSTANCE";
	public static final ValueContainer<SettableValue<?>, SettableValue<ModelSetInstance>> PARENT_MODEL = new ValueContainer<SettableValue<?>, SettableValue<ModelSetInstance>>() {
		private final ModelInstanceType<SettableValue<?>, SettableValue<ModelSetInstance>> theType = ModelTypes.Value
			.forType(ModelSetInstance.class);

		@Override
		public ModelInstanceType<SettableValue<?>, SettableValue<ModelSetInstance>> getType() {
			return theType;
		}

		@Override
		public SettableValue<ModelSetInstance> get(ModelSetInstance models) {
			throw new IllegalStateException("Parent model was not installed");
		}

		@Override
		public String toString() {
			return PARENT_MODEL_NAME;
		}
	};

	public static ModelSetInstance getParentModels(ModelSetInstance models) {
		return models.get(PARENT_MODEL_NAME, PARENT_MODEL.getType()).get();
	}

	public static ObservableModelSet.WrappedBuilder createChildModel(ObservableModelSet parentModels) {
		return parentModels.wrap()//
			.withCustomValue(PARENT_MODEL_NAME, PARENT_MODEL);
	}

	public static ObservableModelSet.WrappedInstanceBuilder createChildModelInstance(ObservableModelSet.Wrapped models,
		ModelSetInstance parentModelInstance) {
		return models.wrap(parentModelInstance)//
			.withCustom(PARENT_MODEL, SettableValue.of(ModelSetInstance.class, parentModelInstance, "Not Reversible"));
	}

	private final CoreSession theWrapped;

	public ExpressoQIS(CoreSession session) {
		theWrapped = session;
	}

	@Override
	public CoreSession getWrapped() {
		return theWrapped;
	}

	public ExpressoParser getExpressoParser() {
		return (ExpressoParser) theWrapped.get("EXPRESSO_PARSER");
	}

	public ExpressoQIS setExpressoParser(ExpressoParser parser) {
		theWrapped.put("EXPRESSO_PARSER", parser);
		return this;
	}

	public ExpressoEnv getExpressoEnv() {
		return (ExpressoEnv) theWrapped.get("EXPRESSO_ENV");
	}

	public ExpressoQIS setExpressoEnv(ExpressoEnv env) {
		theWrapped.put("EXPRESSO_ENV", env);
		return this;
	}

	public ExpressoQIS setModels(ObservableModelSet models, ClassView classView) {
		setExpressoEnv(getExpressoEnv().with(models, classView));
		return this;
	}

	public ObservableExpression getAttributeExpression(String attrName) throws QonfigInterpretationException {
		QonfigExpression expression = getAttribute(attrName, QonfigExpression.class);
		if (expression == null)
			return null;
		try {
			return getExpressoParser().parse(expression.text);
		} catch (ExpressoParseException e) {
			throw new QonfigInterpretationException(e);
		}
	}

	public <M, MV extends M> ValueContainer<M, MV> getAttribute(String attrName, ModelInstanceType<M, MV> type,
		Supplier<Function<ModelSetInstance, MV>> defaultValue) throws QonfigInterpretationException {
		QonfigExpression expression = getAttribute(attrName, QonfigExpression.class);
		if (expression == null) {
			if (defaultValue == null)
				return null;
			return new ValueContainer<M, MV>() {
				final Function<ModelSetInstance, MV> def = defaultValue.get();

				@Override
				public ModelInstanceType<M, MV> getType() {
					return type;
				}

				@Override
				public MV get(ModelSetInstance models) {
					return def.apply(models);
				}
			};
		}
		ObservableExpression obEx;
		try {
			obEx = getExpressoParser().parse(expression.text);
		} catch (ExpressoParseException e) {
			throw new QonfigInterpretationException(e);
		}
		return obEx.evaluate(type, getExpressoEnv());
	}

	public ObservableExpression getValueExpression() throws QonfigInterpretationException {
		QonfigExpression expression = getValue(QonfigExpression.class, null);
		if (expression == null)
			return null;
		try {
			return getExpressoParser().parse(expression.text);
		} catch (ExpressoParseException e) {
			throw new QonfigInterpretationException(e);
		}
	}

	public <M, MV extends M> ValueContainer<M, MV> getValue(ModelInstanceType<M, MV> type,
		Supplier<Function<ModelSetInstance, MV>> defaultValue) throws QonfigInterpretationException {
		Object value = getElement().getValue();
		if (value == null) {
			if (defaultValue == null)
				return null;
			return new ValueContainer<M, MV>() {
				final Function<ModelSetInstance, MV> def = defaultValue.get();

				@Override
				public ModelInstanceType<M, MV> getType() {
					return type;
				}

				@Override
				public MV get(ModelSetInstance models) {
					return def.apply(models);
				}
			};
		} else if (!(value instanceof QonfigExpression))
			throw new QonfigInterpretationException(
				"Value of " + getElement() + " is a " + value.getClass().getName() + ", not an expression");

		ObservableExpression obEx;
		try {
			obEx = getExpressoParser().parse(((QonfigExpression) value).text);
		} catch (ExpressoParseException e) {
			throw new QonfigInterpretationException(e);
		}
		return obEx.evaluate(type, getExpressoEnv());
	}

	public <T> ValueContainer<SettableValue<?>, SettableValue<T>> getAttributeAsValue(String attrName, TypeToken<T> type,
		Supplier<Function<ModelSetInstance, SettableValue<T>>> defaultValue) throws QonfigInterpretationException {
		return getAttribute(attrName, ModelTypes.Value.forType(type), defaultValue);
	}

	public <T> ValueContainer<SettableValue<?>, SettableValue<T>> getAttributeAsValue(String attrName, Class<T> type,
		Supplier<Function<ModelSetInstance, SettableValue<T>>> defaultValue) throws QonfigInterpretationException {
		return getAttributeAsValue(attrName, TypeTokens.get().of(type), defaultValue);
	}

	public <T> ValueContainer<SettableValue<?>, SettableValue<T>> getValueAsValue(TypeToken<T> type,
		Supplier<Function<ModelSetInstance, SettableValue<T>>> defaultValue) throws QonfigInterpretationException {
		return getValue(ModelTypes.Value.forType(type), defaultValue);
	}

	public <T> ValueContainer<SettableValue<?>, SettableValue<T>> getValueAsValue(Class<T> type,
		Supplier<Function<ModelSetInstance, SettableValue<T>>> defaultValue) throws QonfigInterpretationException {
		return getValueAsValue(TypeTokens.get().of(type), defaultValue);
	}

	public <T> ValueContainer<ObservableCollection<?>, ObservableCollection<T>> getAttributeAsCollection(String attrName, TypeToken<T> type,
		Supplier<Function<ModelSetInstance, ObservableCollection<T>>> defaultValue) throws QonfigInterpretationException {
		return getAttribute(attrName, ModelTypes.Collection.forType(type), defaultValue);
	}

	public <T> ValueContainer<ObservableCollection<?>, ObservableCollection<T>> getAttributeAsCollection(String attrName, Class<T> type,
		Supplier<Function<ModelSetInstance, ObservableCollection<T>>> defaultValue) throws QonfigInterpretationException {
		return getAttributeAsCollection(attrName, TypeTokens.get().of(type), defaultValue);
	}

	public <T> ValueContainer<ObservableCollection<?>, ObservableCollection<T>> getValueAsCollection(TypeToken<T> type,
		Supplier<Function<ModelSetInstance, ObservableCollection<T>>> defaultValue) throws QonfigInterpretationException {
		return getValue(ModelTypes.Collection.forType(type), defaultValue);
	}

	public <T> ValueContainer<ObservableCollection<?>, ObservableCollection<T>> getValueAsCollection(Class<T> type,
		Supplier<Function<ModelSetInstance, ObservableCollection<T>>> defaultValue) throws QonfigInterpretationException {
		return getValueAsCollection(TypeTokens.get().of(type), defaultValue);
	}

	@Override
	public String toString() {
		return getWrapped().toString();
	}
}
