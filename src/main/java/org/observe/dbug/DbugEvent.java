package org.observe.dbug;

import java.util.Map;

import org.qommons.Causable;

public class DbugEvent<A> extends Causable.AbstractCausable {
	private final DbugEventHandle<A> theHandle;
	private final Map<String, Object> theParameters;

	DbugEvent(DbugEventHandle<A> handle, Map<String, Object> parameters, Object cause) {
		super(cause);
		theHandle = handle;
		theParameters = parameters;
	}

	public DbugAnchor<A> getAnchor() {
		return theHandle.getAnchor();
	}

	public A getAnchorInstance() {
		return getAnchor().getInstance();
	}

	public DbugEventType<A> getType() {
		return theHandle.getType();
	}

	public Map<String, Object> getParameters() {
		return theParameters;
	}
}
