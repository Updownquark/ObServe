package org.observe.expresso;

import org.observe.expresso.qonfig.ExElement;
import org.qommons.config.QonfigAttributeDef;
import org.qommons.config.QonfigElement.QonfigValue;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.config.QonfigInterpreterCore.CoreSession;
import org.qommons.config.QonfigValueDef;
import org.qommons.config.QonfigValueType;
import org.qommons.config.SpecialSession;
import org.qommons.io.LocatedFilePosition;
import org.qommons.io.LocatedPositionedContent;

/** A special session with extra utility for the Expresso toolkits */
public class ExpressoQIS implements SpecialSession<ExpressoQIS> {
	/** The session key for storing the dynamic value cache */
	public static final String DYNAMIC_VALUE_CACHE = "DYNAMIC_VALUE_CACHE";
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

	/** @return This session's dynamic value cache */
	public DynamicModelValue.Cache getDynamicValueCache() {
		return theWrapped.get(DYNAMIC_VALUE_CACHE, DynamicModelValue.Cache.class);
	}

	@Override
	public ExElement.Def<?> getElementRepresentation() {
		Object er = SpecialSession.super.getElementRepresentation();
		if (er instanceof ExElement.Def<?>)
			return (ExElement.Def<?>) er;
		else
			return null;
	}

	@Override
	public ExpressoQIS setElementRepresentation(Object def) {
		if (!(def instanceof ExElement.Def))
			throw new IllegalArgumentException(
				"Expresso session can only accept representation by an " + ExElement.class.getName() + ".Def implementation");
		SpecialSession.super.setElementRepresentation(def);
		return this;
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
			reporting().error("This element has no value definition");
		else if (!(type.getType() instanceof QonfigValueType.Custom)
			|| !(((QonfigValueType.Custom) type.getType()).getCustomType() instanceof ExpressionValueType))
			reporting().error("Attribute " + type + " is not an expression");
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
		return new CompiledExpression(expression, getElement(), type,
			LocatedPositionedContent.of(getElement().getDocument().getLocation(), value.position), this);
	}

	/**
	 * @param attr The attribute to get
	 * @return The observable expression at the given attribute
	 * @throws QonfigInterpretationException If the attribute expression could not be parsed
	 */
	public CompiledExpression getAttributeExpression(QonfigAttributeDef attr) throws QonfigInterpretationException {
		return getExpression(attr);
	}

	/**
	 * @return The observable expression in this element's value
	 * @throws QonfigInterpretationException If the value expression could not be parsed
	 */
	public CompiledExpression getValueExpression() throws QonfigInterpretationException {
		return getExpression(getValueDef());
	}

	@Override
	public String toString() {
		return getWrapped().toString();
	}
}
