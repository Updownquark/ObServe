package org.observe.dbug;

import java.util.function.Consumer;

import org.qommons.Transaction;
import org.qommons.collect.ListenerList;

public class DbugEventHandle<A> {
	private final DbugAnchor<A> theAnchor;
	private final DbugEventType<A> theType;
	private ListenerList<Consumer<? super DbugEvent<A>>> theListeners;

	DbugEventHandle(DbugAnchor<A> anchor, DbugEventType<A> type) {
		theAnchor = anchor;
		theType = type;
	}

	public DbugAnchor<A> getAnchor() {
		return theAnchor;
	}

	public DbugEventType<A> getType() {
		return theType;
	}

	void listen(Consumer<? super DbugEvent<A>> listener) {
		if (theListeners == null)
			theListeners = ListenerList.build().build();
		theListeners.add(listener, true);
	}

	void fire(Object cause, Consumer<DbugAnchor.EventBuilder<A>> builder) {
		if (theListeners == null)
			return;
		DbugAnchor.EventBuilder<A> eventBuilder = new DbugAnchor.EventBuilder<>(this);
		if (builder != null)
			builder.accept(eventBuilder);
		eventBuilder.fire(cause);
	}

	void fire(DbugEvent<A> event) {
		try (Transaction t = event.use()) {
			theListeners.forEach(//
				l -> l.accept(event));
		}
	}
}
