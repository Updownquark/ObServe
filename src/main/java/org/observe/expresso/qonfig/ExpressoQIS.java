package org.observe.expresso.qonfig;

import org.observe.expresso.CompiledExpressoEnv;
import org.observe.expresso.ExpressoParser;
import org.qommons.config.QonfigInterpreterCore.CoreSession;
import org.qommons.config.SpecialSession;

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
	public CompiledExpressoEnv getExpressoEnv() {
		return theWrapped.get("EXPRESSO_ENV", CompiledExpressoEnv.class);
	}

	/**
	 * @param env The expresso environment to use to evaluate expressions under this session
	 * @return This session
	 */
	public ExpressoQIS setExpressoEnv(CompiledExpressoEnv env) {
		theWrapped.put("EXPRESSO_ENV", env);
		return this;
	}

	/** @return This session's dynamic value cache */
	public ElementModelValue.Cache getElementValueCache() {
		return theWrapped.get(DYNAMIC_VALUE_CACHE, ElementModelValue.Cache.class);
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
		if (def != null && !(def instanceof ExElement.Def))
			throw new IllegalArgumentException(
				"Expresso session can only accept representation by an " + ExElement.class.getName() + ".Def implementation");
		SpecialSession.super.setElementRepresentation(def);
		return this;
	}

	@Override
	public String toString() {
		return getWrapped().toString();
	}
}
