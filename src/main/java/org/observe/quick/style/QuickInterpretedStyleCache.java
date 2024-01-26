package org.observe.quick.style;

import java.util.HashMap;
import java.util.Map;

import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.InterpretedExpressoEnv;
import org.observe.util.TypeTokens;

import com.google.common.reflect.TypeToken;

/** A cache stored in {@link InterpretedExpressoEnv}s to save some work and eliminate duplicate instances of things */
public class QuickInterpretedStyleCache {
	/** The key under which to store instances of this type in the environment */
	public static final String ENV_KEY = "Quick.Interpreted.Style.Cache";
	private final Map<QuickStyleAttributeDef, QuickStyleAttribute<?>> theAttributes;

	/** Creates a cache */
	public QuickInterpretedStyleCache() {
		theAttributes = new HashMap<>();
	}

	/**
	 * @param definition The definition of the style attribute
	 * @return The interpreted style attribute stored in this cache for the given definition
	 */
	public QuickStyleAttribute<?> getAttribute(QuickStyleAttributeDef definition) {
		return theAttributes.get(definition);
	}

	/**
	 * @param definition The definition of the style attribute
	 * @param env The expresso environment to use to determine the attribute's type
	 * @return The interpreted style attribute for the environment
	 * @throws ExpressoInterpretationException If the attribute's type could not be interpreted
	 */
	public QuickStyleAttribute<?> getAttribute(QuickStyleAttributeDef definition, InterpretedExpressoEnv env)
		throws ExpressoInterpretationException {
		if (definition == null)
			return null;
		QuickStyleAttribute<?> attr = theAttributes.get(definition);
		if (attr == null) {
			synchronized (theAttributes) {
				attr = theAttributes.get(definition);
				if (attr == null) {
					// Style attributes always have a null value when not specified, so the type must not be primitive
					// We'll allow specification of primitive types as a shorthand for the wrapper type though
					TypeToken<?> type = TypeTokens.get().wrap(definition.getType().getType(env));
					attr = new QuickStyleAttribute<>(definition, type);
					theAttributes.put(definition, attr);
				}
			}
		}
		return attr;
	}

	/**
	 * @param definition The definition of the style attribute
	 * @param type The type to assert that the attribute should have
	 * @param env The expresso environment to use to determine the attribute's type
	 * @return The interpreted style attribute for the environment
	 * @throws ExpressoInterpretationException If the attribute's type could not be interpreted, or the attribute's type does not match that
	 *         expected
	 */
	public <T> QuickStyleAttribute<T> getAttribute(QuickStyleAttributeDef definition, TypeToken<T> type, InterpretedExpressoEnv env)
		throws ExpressoInterpretationException {
		QuickStyleAttribute<?> attr = getAttribute(definition, env);
		if (!TypeTokens.get().isAssignable(type, attr.getType()))
			throw new ExpressoInterpretationException("Attribute " + attr + " is of type " + attr.getType() + ", not " + type,
				env.reporting().getPosition(), 0);
		return (QuickStyleAttribute<T>) attr;
	}

	/**
	 * @param definition The definition of the style attribute
	 * @param type The type to assert that the attribute should have
	 * @param env The expresso environment to use to determine the attribute's type
	 * @return The interpreted style attribute for the environment
	 * @throws ExpressoInterpretationException If the attribute's type could not be interpreted, or the attribute's type does not match that
	 *         expected
	 */
	public <T> QuickStyleAttribute<T> getAttribute(QuickStyleAttributeDef definition, Class<T> type, InterpretedExpressoEnv env)
		throws ExpressoInterpretationException {
		return getAttribute(definition, TypeTokens.get().of(type), env);
	}

	/**
	 * @param env The expresso environment to get the style cache for
	 * @return The style cache for the environment
	 */
	public static QuickInterpretedStyleCache get(InterpretedExpressoEnv env) {
		QuickInterpretedStyleCache cache = env.get(ENV_KEY, QuickInterpretedStyleCache.class);
		if (cache == null) {
			cache = new QuickInterpretedStyleCache();
			env.put(ENV_KEY, cache);
		}
		return cache;
	}

	/** A cache for {@link InterpretedStyleApplication}s */
	public static class Applications {
		private final Map<StyleApplicationDef, InterpretedStyleApplication> theApplications;

		/** Creates the cache */
		public Applications() {
			theApplications = new HashMap<>();
		}

		/**
		 * @param definition The style application definition to interpret
		 * @param env The expresso environment to interpret the application for
		 * @return The interpreted style application for the definition environment
		 * @throws ExpressoInterpretationException If the style application could not be interpreted
		 */
		public InterpretedStyleApplication getApplication(StyleApplicationDef definition, InterpretedExpressoEnv env)
			throws ExpressoInterpretationException {
			if (definition == null)
				return null;
			InterpretedStyleApplication app = theApplications.get(definition);
			if (app == null) {
				synchronized (theApplications) {
					app = theApplications.get(definition);
					if (app == null) {
						app = definition.interpret(env, this);
						theApplications.put(definition, app);
					}
				}
			}
			return app;
		}
	}
}
