package org.observe.dbug;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.observe.util.TypeTokens;
import org.qommons.Named;

import com.google.common.reflect.TypeToken;

public class DbugEventType<A> implements Named {
	public static class Parameter<A, P> implements Named {
		private final DbugEventType<A> theEvent;
		private final String theName;
		private final TypeToken<P> theType;
		private final DbugAnchorType<? super P> theTarget;

		public Parameter(DbugEventType<A> event, String name, TypeToken<P> type, DbugAnchorType<? super P> target) {
			theEvent = event;
			theName = name;
			theType = type;
			theTarget = target;
		}

		public DbugEventType<A> getEvent() {
			return theEvent;
		}

		@Override
		public String getName() {
			return theName;
		}

		public TypeToken<P> getType() {
			return theType;
		}

		public DbugAnchorType<? super P> getTarget() {
			return theTarget;
		}

		@Override
		public String toString() {
			return theEvent.toString() + "." + theName;
		}
	}

	private final DbugAnchorType<A> theAnchor;
	private final String theName;
	private final Map<String, Parameter<A, ?>> theParameters;

	private DbugEventType(DbugAnchorType<A> anchor, String name, Map<String, Parameter<A, ?>> parameters) {
		theAnchor = anchor;
		theName = name;
		theParameters = parameters;
	}

	public DbugAnchorType<A> getAnchor() {
		return theAnchor;
	}

	@Override
	public String getName() {
		return theName;
	}

	public Map<String, Parameter<A, ?>> getParameters() {
		return theParameters;
	}

	@Override
	public String toString() {
		return theAnchor.getType().getSimpleName() + "." + theName + "()";
	}

	public static class Builder<A> {
		private final DbugEventType<A> theEvent;
		private final String theName;
		private final Map<String, Parameter<A, ?>> theParameters;
		private boolean isBuilt;

		Builder(DbugAnchorType<A> anchor, String name) {
			theName = name;
			theParameters = new LinkedHashMap<>();
			theEvent = new DbugEventType<>(anchor, theName, Collections.unmodifiableMap(theParameters));
		}

		DbugEventType<A> getEvent() {
			return theEvent;
		}

		public DbugAnchorType<A> getAnchor() {
			return theEvent.getAnchor();
		}

		public String getName() {
			return theName;
		}

		public Map<String, Parameter<A, ?>> getParameters() {
			return Collections.unmodifiableMap(theParameters);
		}

		public <P> Builder<A> withParameter(String paramName, TypeToken<P> type) {
			if (isBuilt)
				throw new IllegalStateException("Already built");
			if (theParameters.containsKey(paramName))
				throw new IllegalArgumentException("Parameter " + theEvent + "." + paramName + " is already declared");
			DbugAnchorType<? super P> target = theEvent.getAnchor().getDbug().getAnchor(TypeTokens.getRawType(type), false);
			theParameters.put(paramName, new Parameter<>(theEvent, paramName, type, target));
			return this;
		}

		void built() {
			if (isBuilt)
				throw new IllegalStateException("Already built");
			isBuilt = true;
		}
	}
}
