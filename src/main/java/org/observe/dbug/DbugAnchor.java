package org.observe.dbug;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

import org.observe.util.TypeTokens;
import org.qommons.Transaction;

public class DbugAnchor<A> {
	public static DbugAnchor<?> VOID = new DbugAnchor<>(null, null, Collections.emptySet());

	private final DbugAnchorType<A> theType;
	private final A theInstance;
	private final Set<DbugToken> theTokens;
	private Map<String, DbugField<A, ?>> theFields;
	private Map<String, DbugEventHandle<A>> theEvents;
	private volatile boolean isActive;

	DbugAnchor(DbugAnchorType<A> type, A instance, Set<DbugToken> tokens) {
		theType = type;
		theInstance = instance;
		theTokens = tokens;
		isActive = type != null;
	}

	DbugAnchorType<A> getType() {
		return theType;
	}

	public A getInstance() {
		return theInstance;
	}

	public Set<DbugToken> getTokens() {
		return theTokens;
	}

	public boolean isActive() {
		return isActive;
	}

	public DbugAnchor<A> setActive(boolean active) {
		if (active && theType == null)
			throw new IllegalStateException("Type was not active when this anchor was created--it cannot be activated");
		isActive = active;
		return this;
	}

	public DbugAnchor<A> listenFor(String eventName, Consumer<? super DbugEvent<A>> listener) {
		if (!isActive)
			return this;
		DbugEventHandle<A> event = theEvents == null ? null : theEvents.get(eventName);
		if (event == null) {
			DbugEventType<A> eventType = theType.getEvents().get(eventName);
			if (eventType == null)
				throw new IllegalArgumentException("No such event " + theType.getType().getName() + "." + eventName + "()");
			event = new DbugEventHandle<>(this, eventType);
			if (theEvents == null)
				theEvents = new HashMap<>(theType.getEvents().size() * 3 / 2);
			theEvents.put(eventName, event);
		}
		event.listen(listener);
		return this;
	}

	public Object getField(String fieldName) {
		if (!isActive)
			return null;
		DbugField<A, ?> field = theFields == null ? null : theFields.get(fieldName);
		if (field == null) {
			DbugFieldType<A, ?> fieldType = theType.getFields().get(fieldName);
			if (fieldType == null)
				throw new IllegalArgumentException("No such field " + theType.getType().getName() + "." + fieldName);
			return null;
		} else
			return field.get();
	}

	public <F> DbugAnchor<A> setField(String fieldName, Object value, Object cause) {
		if (!isActive)
			return this;
		DbugField<A, F> field = theFields == null ? null : (DbugField<A, F>) theFields.get(fieldName);
		if (field == null) {
			DbugFieldType<A, F> fieldType = (DbugFieldType<A, F>) theType.getFields().get(fieldName);
			if (fieldType == null)
				throw new IllegalArgumentException("No such field " + theType.getType().getName() + "." + fieldName);
			field = new DbugField<>(this, fieldType, fieldType.castValue(value));
			if (theFields == null)
				theFields = new HashMap<>(theType.getFields().size() * 3 / 2);
			theFields.put(fieldName, field);
		} else {
			field.getType().castValue(value);
			field.set((F) value, cause);
		}
		return this;
	}

	public DbugAnchor<A> event(String eventName, Object cause) {
		return event(eventName, cause, (Consumer<EventBuilder<A>>) null);
	}

	public DbugAnchor<A> event(String eventName, Object cause, Consumer<EventBuilder<A>> builder) {
		if (!isActive)
			return this;
		DbugEventHandle<A> event = theEvents == null ? null : theEvents.get(eventName);
		if (event == null) {
			DbugEventType<A> eventType = theType.getEvents().get(eventName);
			if (eventType == null)
				throw new IllegalArgumentException("No such event " + theType.getType().getName() + "." + eventName + "()");
			return this;
		}
		event.fire(cause, builder);
		return this;
	}

	public InstantiationTransaction instantiating() {
		if (isActive)
			return theType.getDbug().instantiating(theTokens);
		else
			return InstantiationTransaction.VOID;
	}

	void postInit() {
		for(DbugFieldType<A, ?> field : theType.getFields().values()) {
			if (!field.isNullable() && getField(field.getName()) == null)
				throw new IllegalStateException("Non-nullable field " + field + " must be set on instantiation");
		}
		isActive = false;
	}

	@Override
	public String toString() {
		if (theType == null)
			return "VOID";
		return theType.getType().getSimpleName() + ":" + theInstance;
	}

	public interface InstantiationTransaction extends Transaction {
		<A> InstantiationTransaction watchFor(DbugAnchorType<A> targetAnchor, String tokenName,
			Consumer<DbugInstanceTokenizer<A>> configure);

		default <A> InstantiationTransaction watchFor(DbugAnchorType<A> targetAnchor, String tokenName) {
			return watchFor(targetAnchor, tokenName, null);
		}

		static InstantiationTransaction VOID = new InstantiationTransaction() {
			@Override
			public void close() {
			}

			@Override
			public <A> InstantiationTransaction watchFor(DbugAnchorType<A> targetAnchor, String tokenName,
				Consumer<DbugInstanceTokenizer<A>> configure) {
				return this;
			}
		};
	}

	public static class EventBuilder<A> {
		private final DbugEventHandle<A> theEvent;
		private Map<String, Object> theParameters;
		private boolean isFired;

		EventBuilder(DbugEventHandle<A> event) {
			theEvent = event;
		}

		public EventBuilder<A> withParameter(String paramName, Object value) {
			if (isFired)
				throw new IllegalStateException("This event has already been fired");
			if (theParameters == null)
				theParameters = new LinkedHashMap<>(theEvent.getType().getParameters().size() * 3 / 2);
			DbugEventType.Parameter<A, ?> param = theEvent.getType().getParameters().get(paramName);
			if (param == null)
				throw new IllegalArgumentException("No such parameter " + theEvent.getType() + "." + paramName + " declared");
			else if (!TypeTokens.get().isInstance(param.getType(), value))
				throw new IllegalArgumentException("Value " + value + (value == null ? "" : ", type " + value.getClass().getName() + ", ")
					+ " cannot be used for parameter " + paramName + ", type " + param.getType());
			theParameters.put(paramName, value);
			return this;
		}

		void fire(Object cause) {
			isFired = true;
			for (DbugEventType.Parameter<A, ?> param : theEvent.getType().getParameters().values()) {
				Object value = theParameters.get(param.getName());
				if (value == null && param.getType().isPrimitive())
					throw new IllegalStateException("Cannot fire event " + theEvent.getType() + " without parameter " + param.getName());
			}
			theEvent.fire(new DbugEvent<>(theEvent, theParameters, cause));
		}
	}

	public static class DbugInstanceTokenizer<A> {
		private final DbugAnchorType<A> theAnchorType;
		private final String theTokenName;
		private Predicate<? super DbugAnchor<? extends A>> theFilter;
		private boolean isActivating;
		private Consumer<? super DbugAnchor<? extends A>> theAction;
		int theRemainingSkips;
		int theRemainingApplications;

		DbugInstanceTokenizer(DbugAnchorType<A> anchor, String tokenName) {
			theAnchorType = anchor;
			theTokenName = tokenName;
			isActivating = true; // Activate by default
			theRemainingApplications = Integer.MAX_VALUE;
		}

		public DbugAnchorType<A> getAnchorType() {
			return theAnchorType;
		}

		public String getTokenName() {
			return theTokenName;
		}

		public DbugInstanceTokenizer<A> filter(Predicate<? super DbugAnchor<? extends A>> filter) {
			if (theFilter == null)
				theFilter = filter;
			else {
				Predicate<? super DbugAnchor<? extends A>> old = theFilter;
				theFilter = a -> old.test(a) && filter.test(a);
			}
			return this;
		}

		public DbugInstanceTokenizer<A> activate(boolean activate) {
			isActivating = activate;
			return this;
		}

		public DbugInstanceTokenizer<A> thenDo(Consumer<? super DbugAnchor<? extends A>> action) {
			if (theAction == null)
				theAction = action;
			else {
				Consumer<? super DbugAnchor<? extends A>> old = theAction;
				theAction = a -> {
					old.accept(a);
					action.accept(a);
				};
			}
			return this;
		}

		public DbugInstanceTokenizer<A> skip(int skippedItems) {
			theRemainingSkips = skippedItems;
			return this;
		}

		public DbugInstanceTokenizer<A> applyTo(int appliedItems) {
			theRemainingApplications = appliedItems;
			return this;
		}

		boolean applies(DbugAnchor<? extends A> anchor) {
			if (theFilter == null || theFilter.test(anchor)) {
				if (theRemainingSkips > 0) {
					theRemainingSkips--;
					return false;
				}
				theRemainingApplications--;
				if (isActivating)
					anchor.setActive(true);
				if (theAction != null)
					theAction.accept(anchor);
				return true;
			} else
				return false;
		}

		boolean isExhausted() {
			return theRemainingApplications == 0;
		}

		@Override
		public String toString() {
			return theAnchorType + "<-" + theTokenName;
		}
	}
}
