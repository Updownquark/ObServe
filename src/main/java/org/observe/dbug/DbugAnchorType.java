package org.observe.dbug;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import org.observe.util.TypeTokens;

import com.google.common.reflect.TypeToken;

public class DbugAnchorType<A> {
	private final Dbug theDbug;
	private final Class<A> theType;
	private final Map<String, DbugFieldType<A, ?>> theFields;
	private final Map<String, DbugEventType<A>> theEvents;
	private volatile boolean isActive;

	private DbugAnchorType(Dbug dbug, Class<A> type, Map<String, DbugFieldType<A, ?>> fields, Map<String, DbugEventType<A>> events) {
		theDbug = dbug;
		theType = type;
		theFields = fields;
		theEvents = events;
	}

	public Dbug getDbug() {
		return theDbug;
	}

	public Class<A> getType() {
		return theType;
	}

	public Map<String, DbugFieldType<A, ?>> getFields() {
		return theFields;
	}

	public Map<String, DbugEventType<A>> getEvents() {
		return theEvents;
	}

	public boolean isActive() {
		return isActive;
	}

	public DbugAnchorType<A> setActive(boolean active) {
		isActive = active;
		return this;
	}

	public DbugAnchor<A> instance(A value) {
		return instance(value, null);
	}

	public DbugAnchor<A> instance(A value, Consumer<DbugAnchor<A>> configure) {
		if (!isActive)
			return (DbugAnchor<A>) DbugAnchor.VOID;
		Set<DbugToken> tokens = new LinkedHashSet<>();
		DbugAnchor<A> anchor = new DbugAnchor<>(this, value, Collections.unmodifiableSet(tokens));
		if (configure != null)
			configure.accept(anchor);
		anchor.postInit();
		theDbug.getTokens(anchor, tokens);
		return anchor;
	}

	@Override
	public String toString() {
		return theType.getName();
	}

	public static class Builder<A> {
		private final DbugAnchorType<A> theAnchor;
		private final Map<String, DbugFieldType<A, ?>> theFields;
		private final Map<String, DbugEventType<A>> theEvents;
		private boolean isBuilt;

		Builder(Dbug dbug, Class<A> type) {
			theFields = new LinkedHashMap<>();
			theEvents = new LinkedHashMap<>();
			theAnchor = new DbugAnchorType<>(dbug, type, Collections.unmodifiableMap(theFields), Collections.unmodifiableMap(theEvents));
		}

		public DbugAnchorType<A> getAnchor() {
			return theAnchor;
		}

		public Map<String, DbugFieldType<A, ?>> getFields() {
			return Collections.unmodifiableMap(theFields);
		}

		public Map<String, DbugEventType<A>> getEvents() {
			return Collections.unmodifiableMap(theEvents);
		}

		public <F> Builder<A> withField(String fieldName, boolean isFinal, boolean nullable, TypeToken<F> type) {
			if (isBuilt)
				throw new IllegalStateException("Already built");
			else if (theFields.containsKey(fieldName))
				throw new IllegalArgumentException("Field " + theAnchor + "." + fieldName + " is already declared");
			DbugAnchorType<? super F> target = theAnchor.getDbug().getAnchor(TypeTokens.getRawType(type), false);
			theFields.put(fieldName, new DbugFieldType<>(theAnchor, fieldName, type, target, isFinal, nullable));
			return this;
		}

		public Builder<A> withEvent(String eventName) {
			return withEvent(eventName, null);
		}

		public Builder<A> withEvent(String eventName, Consumer<DbugEventType.Builder<A>> builder) {
			if (isBuilt)
				throw new IllegalStateException("Already built");
			else if (theEvents.containsKey(eventName))
				throw new IllegalArgumentException("Event " + theAnchor + "." + eventName + "() is already declared");
			DbugEventType.Builder<A> eventBuilder=new DbugEventType.Builder<>(theAnchor, eventName);
			theEvents.put(eventName, eventBuilder.getEvent());
			if(builder!=null)
				builder.accept(eventBuilder);
			eventBuilder.built();
			return this;
		}

		void built() {
			isBuilt = true;
		}
	}
}
