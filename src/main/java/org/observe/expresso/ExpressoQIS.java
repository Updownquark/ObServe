package org.observe.expresso;

import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.qommons.config.QonfigAttributeDef;
import org.qommons.config.QonfigElement.QonfigValue;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.config.QonfigInterpreterCore.CoreSession;
import org.qommons.config.QonfigValueDef;
import org.qommons.config.QonfigValueType;
import org.qommons.config.SpecialSession;
import org.qommons.io.LocatedFilePosition;

/** A special session with extra utility for the Expresso toolkits */
public class ExpressoQIS implements SpecialSession<ExpressoQIS> {
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

	/** @throws ExpressoInterpretationException If the local model could not be interpreted */
	public void interpretLocalModel() throws ExpressoInterpretationException {
		ObservableModelSet.Built elementModel = (ObservableModelSet.Built) get(ELEMENT_MODEL_KEY);
		if (elementModel != null)
			put(ELEMENT_MODEL_KEY, elementModel.interpret());
	}

	/**
	 * All {@link ObservableModelSet.ModelValueSynth}s for expressions parsed under this session should be
	 * {@link ObservableModelSet.ModelValueSynth#get(ModelSetInstance) satisfied} with a model set wrapped by this method if this element
	 * extends with-local-models.
	 *
	 * @param models The model instance
	 * @return The wrapped model instance containing data for this element's local models
	 * @throws ModelInstantiationException If the local model instantiation fails
	 */
	public ModelSetInstance wrapLocal(ModelSetInstance models) throws ModelInstantiationException {
		ObservableModelSet.Built elementModel = (ObservableModelSet.Built) get(ELEMENT_MODEL_KEY);
		if (elementModel != null && !models.getModel().isRelated(elementModel.getIdentity())) {
			if (!(elementModel instanceof ObservableModelSet.InterpretedModelSet))
				throw new ModelInstantiationException("Local element model was not interpreted.  Should have called interpretLocalModel()",
					getElement().getPositionInFile(), 0);
			models = ((ObservableModelSet.InterpretedModelSet) elementModel).createInstance(models.getUntil()).withAll(models).build();
		}
		return models;
	}

	/**
	 * @param attrName The name of the attribute to get
	 * @return The observable expression at the given attribute
	 * @throws QonfigInterpretationException If the attribute expression could not be parsed
	 */
	public CompiledExpression getAttributeExpression(String attrName) throws QonfigInterpretationException {
		QonfigAttributeDef attr = getAttributeDef(null, null, attrName);
		return getExpression(attr);
	}

	CompiledExpression getExpression(QonfigValueDef type) throws QonfigInterpretationException {
		if (type == null)
			error("This element has no value definition");
		else if (!(type.getType() instanceof QonfigValueType.Custom)
			|| !(((QonfigValueType.Custom) type.getType()).getCustomType() instanceof ExpressionValueType))
			error("Attribute " + type + " is not an expression");
		QonfigValue value;
		if (type instanceof QonfigAttributeDef)
			value = getElement().getAttributes().get(type.getDeclared());
		else
			value = getElement().getValue();
		if (value == null || value.value == null)
			return null;

		ObservableExpression expression;
		try {
			expression = getExpressoParser().parse(((QonfigExpression) value.value).text);
		} catch (ExpressoParseException e) {
			LocatedFilePosition position;
			if (value.position == null || e.getErrorOffset() < 0)
				position = null;
			else
				position = new LocatedFilePosition(getElement().getDocument().getLocation(), value.position.getPosition(e.getErrorOffset()));
			throw new QonfigInterpretationException("Could not parse attribute " + type, position, e.getErrorLength(), e);
		}
		return new CompiledExpression(expression, getElement(), type, value.position, this);
	}

	/**
	 * @param attr The attribute to get
	 * @return The observable expression at the given attribute
	 * @throws QonfigInterpretationException If the attribute expression could not be parsed
	 */
	public CompiledExpression getAttributeExpression(QonfigAttributeDef attr) throws QonfigInterpretationException {
		return getExpression(attr);
	}

	// /**
	// * @param <M> The model type to evaluate the attribute as
	// * @param <MV> The type to evaluate the attribute as
	// * @param attrName The name of the attribute to evaluate
	// * @param type The type to evaluate the attribute as
	// * @param defaultValue Supplies a default value if the given attribute is not specified (optional)
	// * @return The parsed and evaluated attribute expression
	// * @throws QonfigInterpretationException If the attribute expression could not be parsed or evaluated
	// */
	// public <M, MV extends M> ModelValueSynth<M, MV> getAttribute(String attrName, ModelInstanceType<M, MV> type,
	// Supplier<Function<ModelSetInstance, MV>> defaultValue) throws QonfigInterpretationException {
	// QonfigExpression expression = getAttribute(attrName, QonfigExpression.class);
	// if (expression == null) {
	// if (defaultValue == null)
	// return null;
	// return new ModelValueSynth<M, MV>() {
	// final Function<ModelSetInstance, MV> def = defaultValue == null ? null : defaultValue.get();
	//
	// @Override
	// public ModelInstanceType<M, MV> getType() {
	// return type;
	// }
	//
	// @Override
	// public MV get(ModelSetInstance models) {
	// return def == null ? null : def.apply(models);
	// }
	//
	// @Override
	// public MV forModelCopy(MV value, ModelSetInstance sourceModels, ModelSetInstance newModels) {
	// return def == null ? null : def.apply(newModels);
	// }
	//
	// @Override
	// public BetterList<ModelValueSynth<?, ?>> getCores() {
	// return BetterList.of(this);
	// }
	// };
	// }
	// ObservableExpression obEx = getExpressoParser().parse(expression.text);
	// return obEx.evaluate(type, getExpressoEnv());
	// }

	/**
	 * @return The observable expression in this element's value
	 * @throws QonfigInterpretationException If the value expression could not be parsed
	 */
	public CompiledExpression getValueExpression() throws QonfigInterpretationException {
		return getExpression(getValueDef());
	}

	// /**
	// * @param <M> The model type to evaluate the value as
	// * @param <MV> The type to evaluate the value as
	// * @param type The type to evaluate the value as
	// * @param defaultValue Supplies a default value if the value is not specified (optional)
	// * @return The parsed and evaluated value expression
	// * @throws IllegalArgumentException If the value is not an expression
	// * @throws QonfigInterpretationException If the value expression could not be parsed or evaluated as a value
	// */
	// public <M, MV extends M> ModelValueSynth<M, MV> getValue(ModelInstanceType<M, MV> type,
	// Supplier<Function<ModelSetInstance, MV>> defaultValue) throws IllegalArgumentException, QonfigInterpretationException {
	// QonfigValue value = getElement().getValue();
	// if (value == null) {
	// if (defaultValue == null)
	// return null;
	// return new ModelValueSynth<M, MV>() {
	// final Function<ModelSetInstance, MV> def = defaultValue.get();
	//
	// @Override
	// public ModelInstanceType<M, MV> getType() {
	// return type;
	// }
	//
	// @Override
	// public MV get(ModelSetInstance models) {
	// return def.apply(models);
	// }
	//
	// @Override
	// public MV forModelCopy(MV value2, ModelSetInstance sourceModels, ModelSetInstance newModels) {
	// return def == null ? null : def.apply(newModels);
	// }
	//
	// @Override
	// public BetterList<ModelValueSynth<?, ?>> getCores() {
	// return BetterList.of(this);
	// }
	// };
	// } else if (!(value.value instanceof QonfigExpression))
	// throw new IllegalArgumentException(
	// "Value of " + getElement() + " is a " + value.getClass().getName() + ", not an expression");
	//
	// try {
	// ObservableExpression obEx = getExpressoParser().parse(((QonfigExpression) value.value).text);
	// return obEx.evaluate(type, getExpressoEnv());
	// } catch (ExpressoException e) {
	// forChild("value", (value.position == null || e.getErrorOffset() < 0) ? null : value.position.getPosition(e.getErrorOffset()))//
	// .error("Could not parse value", e);
	// throw new IllegalStateException(e); // Should not get here
	// }
	// }
	//
	// /**
	// * Evaluates an attribute as a simple value
	// *
	// * @param <T> The type to evaluate the attribute as
	// * @param attrName The name of the attribute to evaluate
	// * @param type The type to evaluate the attribute as
	// * @param defaultValue Supplies a default value if the given attribute is not specified (optional)
	// * @return The attribute expression, parsed and evaluated as a value
	// * @throws IllegalArgumentException If the attribute value is not an expression
	// * @throws QonfigInterpretationException If the attribute expression could not be parsed or evaluated
	// */
	// public <T> ModelValueSynth<SettableValue<?>, SettableValue<T>> getAttributeAsValue(String attrName, TypeToken<T> type,
	// Supplier<Function<ModelSetInstance, SettableValue<T>>> defaultValue)
	// throws IllegalArgumentException, QonfigInterpretationException {
	// return getAttribute(attrName, ModelTypes.Value.forType(type), defaultValue);
	// }
	//
	// /**
	// * Evaluates an attribute as a simple value
	// *
	// * @param <T> The type to evaluate the attribute as
	// * @param attrName The name of the attribute to evaluate
	// * @param type The type to evaluate the attribute as
	// * @param defaultValue Supplies a default value if the given attribute is not specified (optional)
	// * @return The attribute expression, parsed and evaluated as a value
	// * @throws IllegalArgumentException If the attribute value is not an expression
	// * @throws QonfigInterpretationException If the attribute expression could not be parsed or evaluated as a value
	// */
	// public <T> ModelValueSynth<SettableValue<?>, SettableValue<T>> getAttributeAsValue(String attrName, Class<T> type,
	// Supplier<Function<ModelSetInstance, SettableValue<T>>> defaultValue)
	// throws IllegalArgumentException, QonfigInterpretationException {
	// return getAttributeAsValue(attrName, TypeTokens.get().of(type), defaultValue);
	// }
	//
	// /**
	// * Evaluates this element's value as a simple value
	// *
	// * @param <T> The type to evaluate the value as
	// * @param type The type to evaluate the value as
	// * @param defaultValue Supplies a default value if the value is not specified (optional)
	// * @return The value expression, parsed and evaluated as a value
	// * @throws IllegalArgumentException If the value is not an expression
	// * @throws QonfigInterpretationException If the value expression could not be parsed or evaluated as a value
	// */
	// public <T> ModelValueSynth<SettableValue<?>, SettableValue<T>> getValueAsValue(TypeToken<T> type,
	// Supplier<Function<ModelSetInstance, SettableValue<T>>> defaultValue)
	// throws IllegalArgumentException, QonfigInterpretationException {
	// return getValue(ModelTypes.Value.forType(type), defaultValue);
	// }
	//
	// /**
	// * Evaluates this element's value as a simple value
	// *
	// * @param <T> The element type to evaluate the value as
	// * @param type The type to evaluate the value as
	// * @param defaultValue Supplies a default value if the value is not specified (optional)
	// * @return The value expression, parsed and evaluated as a value
	// * @throws IllegalArgumentException If the value is not an expression
	// * @throws QonfigInterpretationException If the value expression could not be parsed or evaluated as a value
	// */
	// public <T> ModelValueSynth<SettableValue<?>, SettableValue<T>> getValueAsValue(Class<T> type,
	// Supplier<Function<ModelSetInstance, SettableValue<T>>> defaultValue)
	// throws IllegalArgumentException, QonfigInterpretationException {
	// return getValueAsValue(TypeTokens.get().of(type), defaultValue);
	// }
	//
	// /**
	// * Evaluates an attribute as a collection
	// *
	// * @param <T> The element type to evaluate the attribute as
	// * @param attrName The name of the attribute to evaluate
	// * @param type The element type to evaluate the attribute as
	// * @param defaultValue Supplies a default collection if the given attribute is not specified (optional)
	// * @return The attribute expression, parsed and evaluated as a collection
	// * @throws IllegalArgumentException If the attribute value is not an expression
	// * @throws QonfigInterpretationException If the attribute expression could not be parsed or evaluated as a value
	// */
	// public <T> ModelValueSynth<ObservableCollection<?>, ObservableCollection<T>> getAttributeAsCollection(String attrName, TypeToken<T>
	// type,
	// Supplier<Function<ModelSetInstance, ObservableCollection<T>>> defaultValue)
	// throws IllegalArgumentException, QonfigInterpretationException {
	// return getAttribute(attrName, ModelTypes.Collection.forType(type), defaultValue);
	// }
	//
	// /**
	// * Evaluates an attribute as a collection
	// *
	// * @param <T> The element type to evaluate the attribute as
	// * @param attrName The name of the attribute to evaluate
	// * @param type The element type to evaluate the attribute as
	// * @param defaultValue Supplies a default collection if the given attribute is not specified (optional)
	// * @return The attribute expression, parsed and evaluated as a collection
	// * @throws IllegalArgumentException If the attribute value is not an expression
	// * @throws QonfigInterpretationException If the attribute expression could not be parsed or evaluated as a value
	// */
	// public <T> ModelValueSynth<ObservableCollection<?>, ObservableCollection<T>> getAttributeAsCollection(String attrName, Class<T> type,
	// Supplier<Function<ModelSetInstance, ObservableCollection<T>>> defaultValue)
	// throws IllegalArgumentException, QonfigInterpretationException {
	// return getAttributeAsCollection(attrName, TypeTokens.get().of(type), defaultValue);
	// }
	//
	// /**
	// * Evaluates this element's value as a collection
	// *
	// * @param <T> The element type to evaluate the value as
	// * @param type The element type to evaluate the value as
	// * @param defaultValue Supplies a default collection if the value is not specified (optional)
	// * @return The value expression, parsed and evaluated as a collection
	// * @throws IllegalArgumentException If the value is not an expression
	// * @throws QonfigInterpretationException If the value expression could not be parsed or evaluated as a collection
	// */
	// public <T> ModelValueSynth<ObservableCollection<?>, ObservableCollection<T>> getValueAsCollection(TypeToken<T> type,
	// Supplier<Function<ModelSetInstance, ObservableCollection<T>>> defaultValue)
	// throws IllegalArgumentException, QonfigInterpretationException {
	// return getValue(ModelTypes.Collection.forType(type), defaultValue);
	// }
	//
	// /**
	// * Evaluates this element's value as a collection
	// *
	// * @param <T> The element type to evaluate the value as
	// * @param type The element type to evaluate the value as
	// * @param defaultValue Supplies a default collection if the value is not specified (optional)
	// * @return The value expression, parsed and evaluated as a collection
	// * @throws IllegalArgumentException If the value is not an expression
	// * @throws QonfigInterpretationException If the value expression could not be parsed or evaluated as a collection
	// */
	// public <T> ModelValueSynth<ObservableCollection<?>, ObservableCollection<T>> getValueAsCollection(Class<T> type,
	// Supplier<Function<ModelSetInstance, ObservableCollection<T>>> defaultValue)
	// throws IllegalArgumentException, QonfigInterpretationException {
	// return getValueAsCollection(TypeTokens.get().of(type), defaultValue);
	// }

	@Override
	public String toString() {
		return getWrapped().toString();
	}
}
