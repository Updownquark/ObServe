package org.observe.expresso.qonfig;

import org.observe.expresso.CompiledExpressoEnv;
import org.observe.expresso.ExpressoParser;
import org.qommons.config.QonfigInterpreterCore.CoreSession;
import org.qommons.config.SpecialSession;

/** A special session with extra utility for the Expresso toolkits */
public class ExpressoQIS implements SpecialSession<ExpressoQIS> {
	/** The session key for storing the dynamic value cache */
	public static final String DYNAMIC_VALUE_CACHE = "DYNAMIC_VALUE_CACHE";
	private static final String EXPRESSO_ENVS = "EXPRESSO_ENVS";
	private final CoreSession theWrapped;
	private DocumentMap<CompiledExpressoEnv> theExpressoEnvs;

	ExpressoQIS(CoreSession session) {
		theWrapped = session;
		DocumentMap<CompiledExpressoEnv> envs = session.get(EXPRESSO_ENVS, DocumentMap.class);
		ValueSource source = session.getSource(EXPRESSO_ENVS);
		if (source == null) {
			theExpressoEnvs = new DocumentMap<>(null);
			session.put(EXPRESSO_ENVS, theExpressoEnvs);
		} else {
			switch (source) {
			case Inherited:
				theExpressoEnvs = envs.extend();
				session.put(EXPRESSO_ENVS, theExpressoEnvs);
				break;
			default:
				theExpressoEnvs = envs;
				break;
			}
		}
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

	/** @return Compiled expresso environments for each document expressions may reference */
	public DocumentMap<CompiledExpressoEnv> getExpressoEnvs() {
		return theExpressoEnvs;
	}

	/**
	 * @param expressoEnvs Compiled expresso environments for each document expressions may reference
	 * @return This session
	 */
	public ExpressoQIS setExpressoEnvs(DocumentMap<CompiledExpressoEnv> expressoEnvs) {
		theExpressoEnvs = expressoEnvs;
		theWrapped.put(EXPRESSO_ENVS, expressoEnvs);
		return this;
	}

	/**
	 * @param document The document to get the environment for
	 * @return The expresso environment to use to evaluate expressions under this session for the given document
	 */
	public CompiledExpressoEnv getExpressoEnv(String document) {
		return theExpressoEnvs.get(document);
	}

	/**
	 * @param document The document to set the environment for
	 * @param env The expresso environment to use to evaluate expressions under this session for the given document
	 * @return This session
	 */
	public ExpressoQIS setExpressoEnv(String document, CompiledExpressoEnv env) {
		theExpressoEnvs.put(document, env);
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
