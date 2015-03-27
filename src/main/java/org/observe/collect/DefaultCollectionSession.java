package org.observe.collect;

import java.util.HashMap;
import java.util.Map;

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
	}

	private final Object theCause;

	private final Map<Key, Object> theValues;

	/**
	 * Creates the session
	 * 
	 * @param cause The cause of the set of changes to come
	 */
	public DefaultCollectionSession(Object cause) {
		theCause = cause;
		theValues = new HashMap<>();
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
		return theValues.put(new Key(listener, key), value);
	}
}