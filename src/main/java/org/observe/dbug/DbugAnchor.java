package org.observe.dbug;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

import org.observe.ObservableValue;
import org.observe.util.TypeTokens;
import org.qommons.QommonsUtils;
import org.qommons.Transaction;
import org.qommons.collect.BetterList;

public interface DbugAnchor<A> {
	DbugAnchorType<A> getType();

	A getInstance();

	Set<DbugToken> getTokens();

	Transaction tag(String tag);

	boolean isActive();

	Transaction activate();

	DbugAnchor<A> listenFor(String eventName, Consumer<? super DbugEvent<A>> listener);

	Object getField(String fieldName);

	ObservableValue<?> observeField(String fieldName);

	<F> DbugAnchor<A> setField(String fieldName, Object value, Object cause);

	DbugAnchor<A> event(String eventName, Object cause);

	DbugAnchor<A> event(String eventName, Object cause, Consumer<EventBuilder<A>> builder);

	/**
	 * This method may be called by a class that is represented by a debug anchor immediately after the anchor is created. While the
	 * returned transaction is active (not {@link Transaction#close() closed}), new anchors created on the same thread may be captured. This
	 * is useful when e.g. fields of fields of an object are of immediate interest to the object for debugging, but are not easily
	 * accessible.
	 *
	 * @return The instantiation transaction
	 */
	InstantiationTransaction instantiating();

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
					anchor.activate();
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

	public static DbugAnchor<?> VOID=new DbugAnchor<Object>() {
		@Override
		public DbugAnchorType<Object> getType() {
			return null;
		}

		@Override
		public Object getInstance() {
			return null;
		}

		@Override
		public Set<DbugToken> getTokens() {
			return Collections.emptySet();
		}

		@Override
		public Transaction tag(String tag) {
			return Transaction.NONE;
		}

		@Override
		public boolean isActive() {
			return false;
		}

		@Override
		public Transaction activate() {
			return Transaction.NONE;
		}

		@Override
		public DbugAnchor<Object> listenFor(String eventName, Consumer<? super DbugEvent<Object>> listener) {
			return this;
		}

		@Override
		public Object getField(String fieldName) {
			return null;
		}

		@Override
		public ObservableValue<?> observeField(String fieldName) {
			return ObservableValue.of(null);
		}

		@Override
		public <F> DbugAnchor<Object> setField(String fieldName, Object value, Object cause) {
			return this;
		}

		@Override
		public DbugAnchor<Object> event(String eventName, Object cause) {
			return this;
		}

		@Override
		public DbugAnchor<Object> event(String eventName, Object cause, Consumer<EventBuilder<Object>> builder) {
			return this;
		}

		@Override
		public InstantiationTransaction instantiating() {
			return InstantiationTransaction.VOID;
		}

		@Override
		public String toString() {
			return "VOID";
		}
	};

	public class Impl<A> implements DbugAnchor<A> {
		private final DbugAnchorType<A> theType;
		private final A theInstance;
		private final Map<DbugToken, Integer> theTokens;
		private Set<DbugToken> theSafeTokens;
		private final IdentityHashMap<DbugAnchorObserver<? super A>, Transaction> theObservers;
		private volatile long isActive;
		private Map<String, DbugField<A, ?>> theFields;
		private Map<String, DbugEventHandle<A>> theEvents;

		Impl(DbugAnchorType<A> type, A instance, Set<DbugToken> tokens) {
			theType = type;
			theInstance = instance;
			theTokens = new HashMap<>();
			for (DbugToken token : tokens)
				theTokens.put(token, 1);
			theSafeTokens = QommonsUtils.unmodifiableDistinctCopy(theTokens.keySet());
			theObservers = new IdentityHashMap<>();
			checkActive();
		}

		@Override
		public DbugAnchorType<A> getType() {
			return theType;
		}

		@Override
		public A getInstance() {
			return theInstance;
		}

		@Override
		public Set<DbugToken> getTokens() {
			return theSafeTokens;
		}

		@Override
		public synchronized Transaction tag(String tag) {
			DbugToken tagToken = new DbugToken(BetterList.of(tag));
			boolean isNew = theTokens.compute(tagToken, (__, old) -> old == null ? 1 : old + 1).intValue() == 1;
			if (isNew) {
				Set<DbugToken> newTokens = new HashSet<>();
				for (Map.Entry<DbugToken, Integer> token : theTokens.entrySet()) {
					if (token.getKey().getPath().size() > 1)
						newTokens.add(token.getKey().replaceLast(tag));
				}
				for (DbugToken newToken : newTokens)
					theTokens.put(newToken, 1);
				theSafeTokens = QommonsUtils.unmodifiableDistinctCopy(theTokens.keySet());
				checkActive();
			}
			return new Transaction.ReleaseOnceTransaction(() -> {
				synchronized (Impl.this) {
					boolean removed = null == theTokens.compute(tagToken, (__, old) -> old.intValue() == 1 ? null : old - 1);
					if (removed) {
						Iterator<DbugToken> tokenIter = theTokens.keySet().iterator();
						while (tokenIter.hasNext()) {
							if (tokenIter.next().getPath().getLast().equals(tag))
								tokenIter.remove();
						}
						theSafeTokens = QommonsUtils.unmodifiableDistinctCopy(theTokens.keySet());
						checkActive();
					}
				}
			});
		}

		private synchronized void checkActive() {
			boolean wasActive = !theObservers.isEmpty();
			Set<DbugToken> tokens = getTokens();
			List<DbugAnchorObserver<? super A>> newObservers []=new List[1];
			theType.forEachObserver(obs -> {
				boolean applies = obs.applies(tokens);
				if (applies) {
					if (!theObservers.containsKey(obs)) {
						if(newObservers[0]==null)
							newObservers[0]=new ArrayList<>();
						newObservers[0].add(obs);
					}
				} else {
					Transaction t = theObservers.remove(obs);
					if (t != null)
						t.close();
				}
			});
			if (wasActive) {
				if (theObservers.isEmpty())
					isActive--;
			} else if (!theObservers.isEmpty()) {
				isActive++;
			}
			if(newObservers[0]!=null) {
				for(DbugAnchorObserver<? super A> obs : newObservers[0])
					theObservers.put(obs, obs.observe(this));
			}
		}

		@Override
		public boolean isActive() {
			return isActive > 0;
		}

		@Override
		public synchronized Transaction activate() {
			isActive++;
			return new Transaction.ReleaseOnceTransaction(() -> {
				synchronized (Impl.this) {
					isActive--;
				}
			});
		}

		@Override
		public DbugAnchor<A> listenFor(String eventName, Consumer<? super DbugEvent<A>> listener) {
			if (!isActive())
				return this;
			DbugEventHandle<A> event = theEvents == null ? null : theEvents.get(eventName);
			if (event == null) {
				DbugEventType<A> eventType = theType.getEvents().get(eventName);
				if (eventType == null)
					throw new IllegalArgumentException("No such event " + theType.getType().getName() + "." + eventName + "()");
				synchronized (this) {
					if (theEvents == null)
						theEvents = new HashMap<>(theType.getEvents().size() * 3 / 2);
					event = theEvents.get(eventName);
					if (event == null) {
						event = new DbugEventHandle<>(this, eventType);
						theEvents.put(eventName, event);
					}
				}
			}
			event.listen(listener);
			return this;
		}

		@Override
		public Object getField(String fieldName) {
			if (!isActive())
				return null;
			DbugField<A, ?> field = theFields == null ? null : theFields.get(fieldName);
			if (field == null) {
				DbugFieldType<A, ?> fieldType = theType.getFields().get(fieldName);
				if (fieldType == null)
					throw new IllegalArgumentException("No such field " + theType.getType().getName() + "." + fieldName);
				// No need to instantiate. If it hasn't been set, then the value is null.
				return null;
			} else
				return field.get();
		}

		@Override
		public ObservableValue<?> observeField(String fieldName) {
			if (!isActive())
				return null;
			DbugField<A, ?> field = theFields == null ? null : theFields.get(fieldName);
			if (field == null) {
				DbugFieldType<A, ?> fieldType = theType.getFields().get(fieldName);
				if (fieldType == null)
					throw new IllegalArgumentException("No such field " + theType.getType().getName() + "." + fieldName);
				synchronized (this) {
					if (theFields == null)
						theFields = new HashMap<>(theType.getFields().size() * 3 / 2);
					field = theFields.get(fieldName);
					if (field == null) {
						field = new DbugField<>(this, fieldType, null);
						theFields.put(fieldName, field);
					}
				}
				return field.observe();
			} else
				return field.observe();
		}

		@Override
		public <F> DbugAnchor<A> setField(String fieldName, Object value, Object cause) {
			if (!isActive())
				return this;
			DbugField<A, F> field = theFields == null ? null : (DbugField<A, F>) theFields.get(fieldName);
			if (field == null) {
				DbugFieldType<A, F> fieldType = (DbugFieldType<A, F>) theType.getFields().get(fieldName);
				if (fieldType == null)
					throw new IllegalArgumentException("No such field " + theType.getType().getName() + "." + fieldName);
				synchronized (this) {
					if (theFields == null)
						theFields = new HashMap<>(theType.getFields().size() * 3 / 2);
					field = (DbugField<A, F>) theFields.get(fieldName);
					if (field == null) {
						field = new DbugField<>(this, fieldType, fieldType.castValue(value));
						theFields.put(fieldName, field);
					} else
						field.set((F) value, cause);
				}
			} else {
				field.getType().castValue(value);
				field.set((F) value, cause);
			}
			return this;
		}

		@Override
		public DbugAnchor<A> event(String eventName, Object cause) {
			return event(eventName, cause, (Consumer<EventBuilder<A>>) null);
		}

		@Override
		public DbugAnchor<A> event(String eventName, Object cause, Consumer<EventBuilder<A>> builder) {
			if (!isActive())
				return this;
			DbugEventHandle<A> event = theEvents == null ? null : theEvents.get(eventName);
			if (event == null) {
				DbugEventType<A> eventType = theType.getEvents().get(eventName);
				if (eventType == null)
					throw new IllegalArgumentException("No such event " + theType.getType().getName() + "." + eventName + "()");
				synchronized (this) {
					if (theEvents == null)
						theEvents = new HashMap<>(theType.getEvents().size() * 3 / 2);
					event = theEvents.get(eventName);
					if (event == null) {
						event = new DbugEventHandle<>(this, eventType);
						theEvents.put(eventName, event);
					}
				}
			}
			event.fire(cause, builder);
			return this;
		}

		@Override
		public InstantiationTransaction instantiating() {
			if (isActive())
				return theType.getDbug().instantiating(theSafeTokens);
			else
				return InstantiationTransaction.VOID;
		}

		void postInit() {
			for (DbugFieldType<A, ?> field : theType.getFields().values()) {
				if (!field.isNullable() && getField(field.getName()) == null)
					throw new IllegalStateException("Non-nullable field " + field + " must be set on instantiation");
			}
		}

		@Override
		public String toString() {
			return theType.getType().getSimpleName() + ":" + theInstance;
		}
	}
}
