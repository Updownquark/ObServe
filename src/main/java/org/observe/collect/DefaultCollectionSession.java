package org.observe.collect;

/** Simple, default implementation of CollectionSession */
public class DefaultCollectionSession implements CollectionSession {
	private static class Key {
		final Object listener;
		final String key;

		Key(Object listen, String k) {
			this.listener = listen;
			this.key = k;
		}

		@Override
		public int hashCode() {
			return System.identityHashCode(listener) * 13 + key.hashCode();
		}

		@Override
		public boolean equals(Object o) {
			return o instanceof Key && ((Key) o).listener == listener && ((Key) o).key.equals(key);
		}

		@Override
		public String toString() {
			return key + " (" + listener + ")";
		}
	}

	private final Object theCause;

	private final java.util.concurrent.ConcurrentHashMap<Key, Object> theValues;

	/**
	 * Creates the session
	 *
	 * @param cause The cause of the set of changes to come
	 */
	public DefaultCollectionSession(Object cause) {
		theCause = cause;
		theValues = new java.util.concurrent.ConcurrentHashMap<>();
	}

	@Override
	public Object getCause() {
		return theCause;
	}

	@Override
	public Object get(Object listener, String key) {
		return theValues.get(new Key(listener, key));
	}

	@Override
	public Object put(Object listener, String key, Object value) {
		if(value == null)
			return theValues.remove(new Key(listener, key));
		else
			return theValues.put(new Key(listener, key), value);
	}

	@Override
	public Object putIfAbsent(Object listener, String key, Object value) {
		if(value == null)
			return null;
		return theValues.putIfAbsent(new Key(listener, key), value);
	}

	@Override
	public String toString() {
		return "session" + theValues;
	}
}
