package org.observe.quick.style;

import java.util.HashMap;
import java.util.Map;

import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.InterpretedExpressoEnv;
import org.observe.util.TypeTokens;

import com.google.common.reflect.TypeToken;

public class QuickInterpretedStyleCache {
	public static final String ENV_KEY = "Quick.Interpreted.Style.Cache";
	private final Map<QuickStyleAttributeDef, QuickStyleAttribute<?>> theAttributes;

	public QuickInterpretedStyleCache() {
		theAttributes = new HashMap<>();
	}

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

	public <T> QuickStyleAttribute<T> getAttribute(QuickStyleAttributeDef definition, TypeToken<T> type, InterpretedExpressoEnv env)
		throws ExpressoInterpretationException {
		QuickStyleAttribute<?> attr = getAttribute(definition, env);
		if (!TypeTokens.get().isAssignable(type, attr.getType()))
			throw new ExpressoInterpretationException("Attribute " + attr + " is of type " + attr.getType() + ", not " + type,
				env.reporting().getPosition(), 0);
		return (QuickStyleAttribute<T>) attr;
	}

	public <T> QuickStyleAttribute<T> getAttribute(QuickStyleAttributeDef definition, Class<T> type, InterpretedExpressoEnv env)
		throws ExpressoInterpretationException {
		return getAttribute(definition, TypeTokens.get().of(type), env);
	}

	public static QuickInterpretedStyleCache get(InterpretedExpressoEnv env) {
		QuickInterpretedStyleCache cache = env.getProperty(ENV_KEY, QuickInterpretedStyleCache.class);
		if (cache == null) {
			cache = new QuickInterpretedStyleCache();
			env.setProperty(ENV_KEY, cache);
		}
		return cache;
	}

	public static class Applications {
		private final Map<StyleApplicationDef, InterpretedStyleApplication> theApplications;

		public Applications() {
			theApplications = new HashMap<>();
		}

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
