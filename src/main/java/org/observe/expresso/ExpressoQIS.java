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
import org.qommons.collect.BetterList;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.config.QonfigInterpreterCore.CoreSession;
import org.qommons.config.SpecialSession;

import com.google.common.reflect.TypeToken;

/** A special session with extra utility for the Expresso toolkits */
public class ExpressoQIS implements SpecialSession<ExpressoQIS> {
	static final String LOCAL_MODEL_KEY = "ExpressoLocalModel";
	static final String ELEMENT_MODEL_KEY = "ExpressoElementModel";

	private final CoreSession theWrapped;

	ExpressoQIS(CoreSession session) {
		theWrapped = session;
	}

	@Override
	public CoreSession getWrapped() {
		return theWrapped;
	}

	/** @return The expresso parser to use to parse expressions under this session */
	public ExpressoParser getExpressoParser() {
		return (ExpressoParser) theWrapped.get("EXPRESSO_PARSER");
	}

	/**
	 * @param parser The expresso parser to use to parse expressions under this session
	 * @return This session
	 */
	public ExpressoQIS setExpressoParser(ExpressoParser parser) {
		theWrapped.put("EXPRESSO_PARSER", parser);
		return this;
	}

	/** @return The expresso environment to use to evaluate expressions under this session */
	public ExpressoEnv getExpressoEnv() {
		return (ExpressoEnv) theWrapped.get("EXPRESSO_ENV");
	}

	/**
	 * @param env The expresso environment to use to evaluate expressions under this session
	 * @return This session
	 */
	public ExpressoQIS setExpressoEnv(ExpressoEnv env) {
		theWrapped.put("EXPRESSO_ENV", env);
		return this;
	}

	/**
	 * @param models The models to use for evaluating expressions under this session (or null to keep this session's)
	 * @param classView The class view to use for evaluating expressions under this session (or null to keep this session's)
	 * @return This session
	 */
	public ExpressoQIS setModels(ObservableModelSet models, ClassView classView) {
		setExpressoEnv(getExpressoEnv().with(models, classView));
		return this;
	}

	/**
	 * All {@link ObservableModelSet.ValueContainer}s for expressions parsed under this session should be
	 * {@link ObservableModelSet.ValueContainer#get(ModelSetInstance) satisfied} with a model set wrapped by this method if this element
	 * extends with-local-models.
	 *
	 * @param models The model instance
	 * @return The wrapped model instance containing data for this element's local models
	 */
	public ModelSetInstance wrapLocal(ModelSetInstance models) {
		ObservableModelSet elementModel = (ObservableModelSet) get(ELEMENT_MODEL_KEY);
		if (elementModel != null && !models.getModel().isRelated(elementModel.getIdentity()))
			models = elementModel.createInstance(models.getUntil()).withAll(models).build();
		ObservableModelSet localModel = (ObservableModelSet) get(LOCAL_MODEL_KEY);
		if (localModel != null && !models.getModel().isRelated(localModel.getIdentity()))
			models = localModel.createInstance(models.getUntil()).withAll(models).build();
		return models;
	}

	/**
	 * @param attrName The name of the attribute to get
	 * @return The observable expression at the given attribute
	 * @throws QonfigInterpretationException If the attribute expression could not be parsed
	 */
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

	/**
	 * @param <M> The model type to evaluate the attribute as
	 * @param <MV> The type to evaluate the attribute as
	 * @param attrName The name of the attribute to evaluate
	 * @param type The type to evaluate the attribute as
	 * @param defaultValue Supplies a default value if the given attribute is not specified (optional)
	 * @return The parsed and evaluated attribute expression
	 * @throws QonfigInterpretationException If the attribute expression could not be parsed or evaluated
	 */
	public <M, MV extends M> ValueContainer<M, MV> getAttribute(String attrName, ModelInstanceType<M, MV> type,
		Supplier<Function<ModelSetInstance, MV>> defaultValue) throws QonfigInterpretationException {
		QonfigExpression expression = getAttribute(attrName, QonfigExpression.class);
		if (expression == null) {
			if (defaultValue == null)
				return null;
			return new ValueContainer<M, MV>() {
				final Function<ModelSetInstance, MV> def = defaultValue == null ? null : defaultValue.get();

				@Override
				public ModelInstanceType<M, MV> getType() {
					return type;
				}

				@Override
				public MV get(ModelSetInstance models) {
					return def == null ? null : def.apply(models);
				}

				@Override
				public MV forModelCopy(MV value, ModelSetInstance sourceModels, ModelSetInstance newModels) {
					return def == null ? null : def.apply(newModels);
				}

				@Override
				public BetterList<ValueContainer<?, ?>> getCores() {
					return BetterList.of(this);
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

	/**
	 * @return The observable expression in this element's value
	 * @throws QonfigInterpretationException If the value expression could not be parsed
	 */
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

	/**
	 * @param <M> The model type to evaluate the value as
	 * @param <MV> The type to evaluate the value as
	 * @param type The type to evaluate the value as
	 * @param defaultValue Supplies a default value if the value is not specified (optional)
	 * @return The parsed and evaluated value expression
	 * @throws QonfigInterpretationException If the value expression could not be parsed or evaluated
	 */
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

				@Override
				public MV forModelCopy(MV value2, ModelSetInstance sourceModels, ModelSetInstance newModels) {
					return def == null ? null : def.apply(newModels);
				}

				@Override
				public BetterList<ValueContainer<?, ?>> getCores() {
					return BetterList.of(this);
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

	/**
	 * Evaluates an attribute as a simple value
	 *
	 * @param <T> The type to evaluate the attribute as
	 * @param attrName The name of the attribute to evaluate
	 * @param type The type to evaluate the attribute as
	 * @param defaultValue Supplies a default value if the given attribute is not specified (optional)
	 * @return The attribute expression, parsed and evaluated as a value
	 * @throws QonfigInterpretationException If the attribute expression could not be parsed or evaluated
	 */
	public <T> ValueContainer<SettableValue<?>, SettableValue<T>> getAttributeAsValue(String attrName, TypeToken<T> type,
		Supplier<Function<ModelSetInstance, SettableValue<T>>> defaultValue) throws QonfigInterpretationException {
		return getAttribute(attrName, ModelTypes.Value.forType(type), defaultValue);
	}

	/**
	 * Evaluates an attribute as a simple value
	 *
	 * @param <T> The type to evaluate the attribute as
	 * @param attrName The name of the attribute to evaluate
	 * @param type The type to evaluate the attribute as
	 * @param defaultValue Supplies a default value if the given attribute is not specified (optional)
	 * @return The attribute expression, parsed and evaluated as a value
	 * @throws QonfigInterpretationException If the attribute expression could not be parsed or evaluated
	 */
	public <T> ValueContainer<SettableValue<?>, SettableValue<T>> getAttributeAsValue(String attrName, Class<T> type,
		Supplier<Function<ModelSetInstance, SettableValue<T>>> defaultValue) throws QonfigInterpretationException {
		return getAttributeAsValue(attrName, TypeTokens.get().of(type), defaultValue);
	}

	/**
	 * Evaluates this element's value as a simple value
	 *
	 * @param <T> The type to evaluate the value as
	 * @param type The type to evaluate the value as
	 * @param defaultValue Supplies a default value if the value is not specified (optional)
	 * @return The value expression, parsed and evaluated as a value
	 * @throws QonfigInterpretationException If the value expression could not be parsed or evaluated
	 */
	public <T> ValueContainer<SettableValue<?>, SettableValue<T>> getValueAsValue(TypeToken<T> type,
		Supplier<Function<ModelSetInstance, SettableValue<T>>> defaultValue) throws QonfigInterpretationException {
		return getValue(ModelTypes.Value.forType(type), defaultValue);
	}

	/**
	 * Evaluates this element's value as a simple value
	 *
	 * @param <T> The element type to evaluate the value as
	 * @param type The type to evaluate the value as
	 * @param defaultValue Supplies a default value if the value is not specified (optional)
	 * @return The value expression, parsed and evaluated as a value
	 * @throws QonfigInterpretationException If the value expression could not be parsed or evaluated
	 */
	public <T> ValueContainer<SettableValue<?>, SettableValue<T>> getValueAsValue(Class<T> type,
		Supplier<Function<ModelSetInstance, SettableValue<T>>> defaultValue) throws QonfigInterpretationException {
		return getValueAsValue(TypeTokens.get().of(type), defaultValue);
	}

	/**
	 * Evaluates an attribute as a collection
	 *
	 * @param <T> The element type to evaluate the attribute as
	 * @param attrName The name of the attribute to evaluate
	 * @param type The element type to evaluate the attribute as
	 * @param defaultValue Supplies a default collection if the given attribute is not specified (optional)
	 * @return The attribute expression, parsed and evaluated as a collection
	 * @throws QonfigInterpretationException If the attribute expression could not be parsed or evaluated
	 */
	public <T> ValueContainer<ObservableCollection<?>, ObservableCollection<T>> getAttributeAsCollection(String attrName, TypeToken<T> type,
		Supplier<Function<ModelSetInstance, ObservableCollection<T>>> defaultValue) throws QonfigInterpretationException {
		return getAttribute(attrName, ModelTypes.Collection.forType(type), defaultValue);
	}

	/**
	 * Evaluates an attribute as a collection
	 *
	 * @param <T> The element type to evaluate the attribute as
	 * @param attrName The name of the attribute to evaluate
	 * @param type The element type to evaluate the attribute as
	 * @param defaultValue Supplies a default collection if the given attribute is not specified (optional)
	 * @return The attribute expression, parsed and evaluated as a collection
	 * @throws QonfigInterpretationException If the attribute expression could not be parsed or evaluated
	 */
	public <T> ValueContainer<ObservableCollection<?>, ObservableCollection<T>> getAttributeAsCollection(String attrName, Class<T> type,
		Supplier<Function<ModelSetInstance, ObservableCollection<T>>> defaultValue) throws QonfigInterpretationException {
		return getAttributeAsCollection(attrName, TypeTokens.get().of(type), defaultValue);
	}

	/**
	 * Evaluates this element's value as a collection
	 *
	 * @param <T> The element type to evaluate the value as
	 * @param type The element type to evaluate the value as
	 * @param defaultValue Supplies a default collection if the value is not specified (optional)
	 * @return The value expression, parsed and evaluated as a collection
	 * @throws QonfigInterpretationException If the value expression could not be parsed or evaluated
	 */
	public <T> ValueContainer<ObservableCollection<?>, ObservableCollection<T>> getValueAsCollection(TypeToken<T> type,
		Supplier<Function<ModelSetInstance, ObservableCollection<T>>> defaultValue) throws QonfigInterpretationException {
		return getValue(ModelTypes.Collection.forType(type), defaultValue);
	}

	/**
	 * Evaluates this element's value as a collection
	 *
	 * @param <T> The element type to evaluate the value as
	 * @param type The element type to evaluate the value as
	 * @param defaultValue Supplies a default collection if the value is not specified (optional)
	 * @return The value expression, parsed and evaluated as a collection
	 * @throws QonfigInterpretationException If the value expression could not be parsed or evaluated
	 */
	public <T> ValueContainer<ObservableCollection<?>, ObservableCollection<T>> getValueAsCollection(Class<T> type,
		Supplier<Function<ModelSetInstance, ObservableCollection<T>>> defaultValue) throws QonfigInterpretationException {
		return getValueAsCollection(TypeTokens.get().of(type), defaultValue);
	}

	@Override
	public String toString() {
		return getWrapped().toString();
	}
}
