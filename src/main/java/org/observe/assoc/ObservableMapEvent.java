package org.observe.assoc;

import org.observe.collect.CollectionChangeType;
import org.observe.collect.CollectionElementMove;
import org.observe.collect.ObservableCollectionEvent;
import org.qommons.Causable;
import org.qommons.Transaction;
import org.qommons.collect.BetterList;
import org.qommons.collect.ElementId;
import org.qommons.collect.MapEntryHandle;

/**
 * An event representing a change to a {@link ObservableMap}
 *
 * @param <K> The key-type of the map
 * @param <V> The value-type of the map
 */
public interface ObservableMapEvent<K, V> extends ObservableCollectionEvent<V> {
	/** @return The previous key for the entry which was added/removed/changed */
	K getOldKey();

	/** @return The key for the entry which was added/removed/changed */
	K getKey();

	/**
	 * Default {@link ObservableMapEvent} implementation
	 *
	 * @param <K> The key-type of the map
	 * @param <V> The value-type of the map
	 */
	public static class Default<K, V> extends ObservableCollectionEvent.DefaultObservableCollectionEvent<V>
	implements ObservableMapEvent<K, V> {
		private final K theOldKey;
		private final K theKey;

		/**
		 * @param elementId The element ID of the entry in the map entry's value collection that was added/removed changed
		 * @param index The index in the entry's value collection of the element that was added/removed/changed
		 * @param type The type of the change (addition/removal/change)
		 * @param oldKey The previous key. This will only be different from <code>key</code> if this event represents a modification to a
		 *        key value that does not affect the contents of the key's values. In this case, {@link #getIndex()} will be -1
		 * @param key The key under which a value was added/removed/changed
		 * @param oldValue The value of the element before the change (for change type of {@link CollectionChangeType#set set} only)
		 * @param newValue The value of the element after the change
		 * @param causes The causes of the change
		 */
		public Default(ElementId elementId, int index, CollectionChangeType type, K oldKey, K key, V oldValue, V newValue,
			Object... causes) {
			super(elementId, index, type, oldValue, newValue, causes);
			theOldKey = oldKey;
			theKey = key;
		}

		@Override
		protected void checkIndex(int index) {
		}

		@Override
		public K getOldKey() {
			return theOldKey;
		}

		@Override
		public K getKey() {
			return theKey;
		}

		@Override
		public String toString() {
			StringBuilder str = new StringBuilder().append('[').append(getElementId()).append(':').append(getKey()).append("]: ");
			switch (getType()) {
			case add:
				str.append("+:").append(getNewValue());
				break;
			case remove:
				str.append("-:").append(getOldValue());
				break;
			case set:
				str.append(':').append(getOldValue()).append("->").append(getNewValue());
				break;
			}
			return str.toString();
		}
	}

	/**
	 * An {@link ObservableMapEvent} derived from a {@link ObservableMultiMapEvent}
	 *
	 * @param <K> The key type of the source and derived maps
	 * @param <V> The value type of the source and derived maps
	 */
	public static class MultiToSingleMapEvent<K, V> implements ObservableMapEvent<K, V> {
		private final ObservableMultiMapEvent<? extends K, ? extends V> theSource;
		private final CollectionChangeType theType;
		private final V theNewValue;

		/**
		 * @param source The source multi-map event
		 * @param type The type of the change
		 * @param newValue The new map value for this event
		 */
		public MultiToSingleMapEvent(ObservableMultiMapEvent<? extends K, ? extends V> source, CollectionChangeType type, V newValue) {
			theSource = source;
			theType = type;
			theNewValue = newValue;
		}

		@Override
		public ElementId getElementId() {
			return theSource.getElementId();
		}

		@Override
		public int getIndex() {
			return theSource.getKeyIndex();
		}

		@Override
		public CollectionChangeType getType() {
			return theType;
		}

		@Override
		public CollectionElementMove getMovement() {
			if (theType != CollectionChangeType.set)
				return theSource.getMovement();
			else
				return null;
		}

		@Override
		public ObservableCollectionEvent<V> derive(ElementId element, int index) {
			if (element.equals(getElementId()))
				return this;
			return new ElementChangedCollectionEvent<>(this, element, index);
		}

		@Override
		public <E2> ObservableCollectionEvent<E2> derive(ElementId element, int index, E2 oldValue, E2 newValue) {
			if (element.equals(getElementId()) && getOldValue() == oldValue && getNewValue() == newValue)
				return (ObservableCollectionEvent<E2>) this;
			return new ValueChangedCollectionEvent<>(this, element, index, oldValue, newValue);
		}

		@Override
		public BetterList<Object> getCauses() {
			return BetterList.of(theSource);
		}

		@Override
		public Causable getRootCausable() {
			return theSource.getRootCausable();
		}

		@Override
		public Effect onFinish(CausableKey key) {
			return theSource.onFinish(key);
		}

		@Override
		public boolean isFinished() {
			return theSource.isFinished();
		}

		@Override
		public boolean isTerminated() {
			return theSource.isTerminated();
		}

		@Override
		public Transaction use() {
			return Transaction.NONE; // The source event is already in use
		}

		@Override
		public boolean isInitial() {
			return theSource.isInitial();
		}

		@Override
		public V getOldValue() {
			return theSource.getOldValue();
		}

		@Override
		public V getNewValue() {
			return theNewValue;
		}

		@Override
		public K getOldKey() {
			return theSource.getOldKey();
		}

		@Override
		public K getKey() {
			return theSource.getOldKey();
		}
	}

	/**
	 * An {@link ObservableMapEvent} derived from a change to the {@link ObservableMap#entrySet()} of an {@link ObservableMap}
	 *
	 * @param <K> The key type of the map
	 * @param <V> The value type of the map
	 */
	public static class FromEntryEvent<K, V> implements ObservableMapEvent<K, V> {
		private final ObservableCollectionEvent<? extends MapEntryHandle<? extends K, ? extends V>> theSource;
		private final K theOldKey;
		private final V theOldValue;
		private final K theNewKey;
		private final V theNewValue;

		/** @param source The change event from the {@link ObservableMap#entrySet()} of the map */
		public FromEntryEvent(ObservableCollectionEvent<? extends MapEntryHandle<? extends K, ? extends V>> source) {
			theSource = source;
			MapEntryHandle<? extends K, ? extends V> entry = theSource.getOldValue();
			theOldKey = entry == null ? null : entry.getKey();
			theOldValue = entry == null ? null : entry.getValue();

			entry = theSource.getNewValue();
			theNewKey = entry == null ? null : entry.getKey();
			theNewValue = entry == null ? null : entry.getValue();
		}

		@Override
		public ElementId getElementId() {
			return theSource.getElementId();
		}

		@Override
		public int getIndex() {
			return theSource.getIndex();
		}

		@Override
		public CollectionChangeType getType() {
			return theSource.getType();
		}

		@Override
		public CollectionElementMove getMovement() {
			return theSource.getMovement();
		}

		@Override
		public ObservableCollectionEvent<V> derive(ElementId element, int index) {
			if (element.equals(getElementId()))
				return this;
			return new ElementChangedCollectionEvent<>(this, element, index);
		}

		@Override
		public <E2> ObservableCollectionEvent<E2> derive(ElementId element, int index, E2 oldValue, E2 newValue) {
			if (element.equals(getElementId()) && getOldValue() == oldValue && getNewValue() == newValue)
				return (ObservableCollectionEvent<E2>) this;
			return new ValueChangedCollectionEvent<>(this, element, index, oldValue, newValue);
		}

		@Override
		public boolean isInitial() {
			return theSource.isInitial();
		}

		@Override
		public BetterList<Object> getCauses() {
			return BetterList.of(theSource);
		}

		@Override
		public Causable getRootCausable() {
			return theSource.getRootCausable();
		}

		@Override
		public Effect onFinish(CausableKey key) {
			return theSource.onFinish(key);
		}

		@Override
		public boolean isFinished() {
			return theSource.isFinished();
		}

		@Override
		public boolean isTerminated() {
			return theSource.isTerminated();
		}

		@Override
		public Transaction use() {
			return Transaction.NONE;
		}

		@Override
		public K getOldKey() {
			return theOldKey;
		}

		@Override
		public K getKey() {
			return theNewKey;
		}

		@Override
		public V getOldValue() {
			return theOldValue;
		}

		@Override
		public V getNewValue() {
			return theNewValue;
		}
	}
}
