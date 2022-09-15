package org.observe.dbug;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.observe.util.TypeTokens;
import org.qommons.IdentityKey;
import org.qommons.Transaction;

import com.google.common.reflect.TypeToken;

public class DbugAnchorType<A> {
	private final Dbug theDbug;
	private final Class<A> theType;
	private final Map<String, DbugFieldType<A, ?>> theFields;
	private final Map<String, DbugEventType<A>> theEvents;
	private WeakHashMap<IdentityKey<A>, DbugAnchor<A>> theInstances;
	private final AtomicInteger isActive;

	private DbugAnchorType(Dbug dbug, Class<A> type, Map<String, DbugFieldType<A, ?>> fields, Map<String, DbugEventType<A>> events) {
		theDbug = dbug;
		theType = type;
		theFields = fields;
		theEvents = events;
		isActive = new AtomicInteger();
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
		return isActive.get() > 0;
	}

	public DbugAnchorType<A> setActive(boolean active) {
		if (active)
			isActive.getAndIncrement();
		else
			isActive.getAndDecrement();
		return this;
	}

	public Transaction activate() {
		isActive.getAndIncrement();
		return isActive::getAndDecrement;
	}

	public DbugAnchor<A> instance(A value) {
		return instance(value, null);
	}

	public DbugAnchor<A> instance(A value, Consumer<DbugAnchor<A>> configure) {
		if (!isActive())
			return (DbugAnchor<A>) DbugAnchor.VOID;
		DbugAnchor<A> anchor;
		synchronized (this) {
			if (theInstances == null)
				theInstances = new WeakHashMap<>();
			Set<DbugToken>[] tokens = new Set[1];
			anchor = theInstances.computeIfAbsent(new IdentityKey<>(value), __ -> {
				tokens[0] = new LinkedHashSet<>();
				return new DbugAnchor<>(this, value, Collections.unmodifiableSet(tokens[0]));
			});
			if (tokens[0] != null) { // Created just now
				if (configure != null)
					configure.accept(anchor);
				anchor.postInit();
				theDbug.getTokens(anchor, tokens[0]);
			}
		}
		return anchor;
	}

	public DbugAnchor<A> getInstance(A value) {
		if (theInstances == null)
			return null;
		synchronized (this) {
			return theInstances.get(new IdentityKey<>(value));
		}
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
