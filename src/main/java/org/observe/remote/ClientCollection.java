package org.observe.remote;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import org.observe.Equivalence;
import org.observe.Subscription;
import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableCollectionEvent;
import org.observe.remote.CollectionClientTransceiver.ByteList;
import org.observe.remote.CollectionClientTransceiver.ConcurrentRemoteModException;
import org.observe.remote.CollectionClientTransceiver.LockResult;
import org.observe.remote.CollectionClientTransceiver.CollectionPollResult;
import org.observe.remote.CollectionClientTransceiver.SerializedCollectionChange;
import org.observe.util.ObservableCollectionWrapper;
import org.qommons.Causable;
import org.qommons.Lockable;
import org.qommons.Transactable;
import org.qommons.Transaction;
import org.qommons.collect.BetterCollection;
import org.qommons.collect.BetterSortedList.SortedSearchFilter;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.ElementId;
import org.qommons.collect.MutableCollectionElement;
import org.qommons.collect.RRWLockingStrategy;
import org.qommons.ex.CheckedExceptionWrapper;
import org.qommons.tree.BetterTreeList;

import com.google.common.reflect.TypeToken;

public class ClientCollection<E, VP, CP, O> extends ObservableCollectionWrapper<E> {
	public static final String NO_CONNECTION = "Connection could not be made";
	private final Transactable theLock;
	final CollectionConnectionSerializer<E, VP, CP> theSerializer;
	final CollectionClientTransceiver<VP, O> theTransceiver;
	final BetterTreeList<ValueElement> theElements;
	private final ObservableCollection<ValueElement> theObservableElements;
	private final ObservableCollection<E> theValues;

	public ClientCollection(TypeToken<E> type, Equivalence<? super E> equivalence, CollectionConnectionSerializer<E, VP, CP> serializer,
		CollectionClientTransceiver<VP, O> transceiver) {
		theLock = new RRWLockingStrategy();
		theSerializer = serializer;
		theTransceiver = transceiver;
		theElements = BetterTreeList.<ValueElement> build().safe(false).build();
		theObservableElements = ObservableCollection.build(new TypeToken<ValueElement>() {}).withBacking(theElements).safe(false).build();
		theValues = theObservableElements.flow().map(type, ve -> ve.theValue, opts -> opts.cache(false)).withEquivalence(equivalence)
			.collectPassive();
		init(theValues);
	}

	Transaction lockLocal(boolean write) {
		return theLock.lock(write, null);
	}

	<R extends CollectionPollResult<VP>> R applyChanges(R changes) {
		try (Transaction t = lockLocal(true); Transaction obsT = theValues.lock(true, null)) {
			for (SerializedCollectionChange<VP> change : changes.changes) {
				switch (change.type) {
				case add:
					CollectionElement<ValueElement> valueEl = theElements.search(el -> -el.compareTo(change.elementId),
						SortedSearchFilter.Less);
					if (valueEl == null)
						theObservableElements.add(new ValueElement(change.elementId, theSerializer.deserializeValue(change.newValue)));
					else
						theObservableElements.addElement(
							new ValueElement(change.elementId, theSerializer.deserializeValue(change.newValue)), //
							valueEl.getElementId(), null, true);
					break;
				case remove:
					valueEl = theElements.search(el -> -el.compareTo(change.elementId), SortedSearchFilter.OnlyMatch);
					theObservableElements.mutableElement(valueEl.getElementId()).remove();
					break;
				case set:
					valueEl = theElements.search(el -> -el.compareTo(change.elementId), SortedSearchFilter.OnlyMatch);
					E newValue = theSerializer.deserializeValue(change.newValue);
					if (change.oldValue == change.newValue)
						valueEl.get().oldValue = newValue;
					else
						valueEl.get().oldValue = theSerializer.deserializeValue(change.oldValue);
					theObservableElements.mutableElement(valueEl.getElementId()).set(valueEl.get());
					valueEl.get().oldValue = null;
					break;
				}
				theTransceiver.setLastChange(change.eventId);
			}
		}
		return changes;
	}

	@Override
	public Subscription onChange(Consumer<? super ObservableCollectionEvent<? extends E>> observer) {
		return super.onChange(evt -> {
			switch (evt.getType()) {
			case set:
				ValueElement el = theElements.getElement(evt.getElementId()).get();
				ObservableCollectionEvent<E> overrideEvt = new ObservableCollectionEvent<>(evt.getElementId(), getType(), evt.getIndex(),
					evt.getType(), el.oldValue, el.theValue, evt.getCause());
				try (Transaction evtT = Causable.use(overrideEvt)) {
					observer.accept(overrideEvt);
				}
				break;
			default:
				observer.accept(evt);
				break;
			}
		});
	}

	public boolean pollChanges() {
		try (Transaction t = lockLocal(true)) {
			CollectionPollResult<VP> result;
			try {
				result = theTransceiver.poll();
			} catch (IOException e) {
				return false;
			}
			applyChanges(result);
			return !result.changes.isEmpty();
		}
	}

	@Override
	public boolean isContentControlled() {
		try {
			return theTransceiver.isContentControlled();
		} catch (IOException e) {
			return true;
		}
	}

	@Override
	public long getStamp() {
		return theTransceiver.getLastChange();
	}

	private class RemoteLockable implements Lockable {
		private final boolean isWrite;

		RemoteLockable(boolean write) {
			isWrite = write;
		}

		@Override
		public boolean isLockSupported() {
			return true;
		}

		@Override
		public Transaction lock() {
			try {
				LockResult<VP> lockResult = theTransceiver.lock(isWrite);
				if (isWrite)
					applyChanges(lockResult);
				return lockResult.get();
			} catch (IOException e) {
				throw new CheckedExceptionWrapper(e);
			}
		}

		@Override
		public Transaction tryLock() {
			try {
				LockResult<VP> lockResult = theTransceiver.tryLock(isWrite);
				if (isWrite)
					applyChanges(lockResult);
				return lockResult.get();
			} catch (IOException e) {
				throw new CheckedExceptionWrapper(e);
			}
		}
	}

	@Override
	public Transaction lock(boolean write, Object cause) {
		return Lockable.lockAll(//
			Lockable.lockable(theLock, write, cause), //
			new RemoteLockable(write), //
			Lockable.lockable(theValues, write, cause));
	}

	@Override
	public Transaction tryLock(boolean write, Object cause) {
		return Lockable.tryLockAll(//
			Lockable.lockable(theLock, write, cause), //
			new RemoteLockable(write), //
			Lockable.lockable(theValues, write, cause));
	}

	@Override
	public String canAdd(E value, ElementId after, ElementId before) {
		VP serialized = null;
		while (true) {
			try {
				try (Transaction t = lockLocal(false)) {
					ByteList afterAddr = after == null ? null : theElements.getElement(after).get().address;
					ByteList beforeAddr = before == null ? null : theElements.getElement(before).get().address;
					if (serialized == null)
						serialized = theSerializer.serializeValue(value);
					O query = theTransceiver.add(serialized, afterAddr, beforeAddr, false);
					return theTransceiver.queryCapability(Arrays.asList(query));
				}
			} catch (IOException e) {
				return NO_CONNECTION;
			} catch (ConcurrentRemoteModException e) {
				applyChanges(e.getChanges());
			}
		}
	}

	@Override
	public CollectionElement<E> addElement(E value, ElementId after, ElementId before, boolean first)
		throws UnsupportedOperationException, IllegalArgumentException {
		VP serialized = null;
		try (Transaction t = lockLocal(true)) {
			while (true) {
				ByteList afterAddr = after == null ? null : theElements.getElement(after).get().address;
				ByteList beforeAddr = before == null ? null : theElements.getElement(before).get().address;
				if (serialized == null)
					serialized = theSerializer.serializeValue(value);
				O op = theTransceiver.add(serialized, afterAddr, beforeAddr, first);
				ByteList added;
				try {
					added = applyChanges(theTransceiver.applyOperations(Arrays.asList(op))).throwIfError().getElement();
				} catch (IOException e) {
					throw new UnsupportedOperationException(NO_CONNECTION, e);
				} catch (ConcurrentRemoteModException e) {
					applyChanges(e.getChanges());
					continue;
				}
				if (added == null)
					return null;
				CollectionElement<ValueElement> found = theElements.search(el -> -el.compareTo(added), SortedSearchFilter.OnlyMatch);
				if (found == null)
					throw new IllegalStateException("No such element");
				else
					return theValues.getElement(found.getElementId());
			}
		}
	}

	@Override
	public void setValue(Collection<ElementId> elements, E value) {
		List<O> ops = new ArrayList<>(elements.size());
		VP serialized = theSerializer.serializeValue(value);
		try (Transaction t = lockLocal(true)) {
			while (true) {
				for (ElementId el : elements) {
					ValueElement valueEl = theElements.getElement(el).get();
					ops.add(theTransceiver.set(valueEl.address, serialized));
				}
				try {
					applyChanges(theTransceiver.applyOperations(ops)).throwIfError();
				} catch (IOException e) {
					throw new UnsupportedOperationException(NO_CONNECTION, e);
				} catch (ConcurrentRemoteModException e) {
					ops.clear();
					applyChanges(e.getChanges());
				}
			}
		}
	}

	@Override
	public MutableCollectionElement<E> mutableElement(ElementId id) {
		try (Transaction t = lockLocal(false)) {
			return theElements.getElement(id).get();
		}
	}

	@Override
	public void clear() {
		try (Transaction t = lockLocal(true)) {
			while (true) {
				List<O> ops = new ArrayList<>(size());
				for (CollectionElement<ValueElement> el : theElements.elements())
					ops.add(theTransceiver.remove(el.get().address));
				try {
					applyChanges(theTransceiver.applyOperations(ops)).throwIfError();
				} catch (IOException e) {
					throw new UnsupportedOperationException(NO_CONNECTION, e);
				} catch (ConcurrentRemoteModException e) {
					applyChanges(e.getChanges());
					continue;
				}
			}
		}
	}

	@Override
	public boolean addAll(int index, Collection<? extends E> c) {
		if (index < 0)
			throw new IndexOutOfBoundsException("" + index);
		try (Transaction t = lockLocal(true); //
			Transaction ct = Transactable.lock(c, false, null)) {
			while (true) {
				ByteList afterAddr, beforeAddr;
				if (isEmpty()) {
					if (index != 0)
						throw new IndexOutOfBoundsException(index + " of 0");
					afterAddr = null;
					beforeAddr = theElements.getTerminalElement(true).get().address;
				} else if (index == size()) {
					beforeAddr = null;
					afterAddr = theElements.getTerminalElement(false).get().address;
				} else {
					CollectionElement<ValueElement> indexEl = theElements.getElement(index);
					beforeAddr = indexEl.get().address;
					afterAddr = index == 0 ? null : theElements.getAdjacentElement(indexEl.getElementId(), false).get().address;
				}
				List<O> ops = new ArrayList<>(c.size());
				for (E v : c) {
					VP serialized = theSerializer.serializeValue(v);
					ops.add(theTransceiver.add(serialized, afterAddr, beforeAddr, false));
				}
				try {
					return applyChanges(theTransceiver.applyOperations(ops)).throwIfError().getElement() != null;
				} catch (IOException e) {
					throw new UnsupportedOperationException(NO_CONNECTION, e);
				} catch (ConcurrentRemoteModException e) {
					applyChanges(e.getChanges());
					continue;
				}
			}
		}
	}

	@Override
	public boolean addAll(Collection<? extends E> c) {
		try (Transaction t = lockLocal(true); //
			Transaction ct = Transactable.lock(c, false, null)) {
			while (true) {
				List<O> ops = new ArrayList<>(c.size());
				for (E v : c) {
					VP serialized = theSerializer.serializeValue(v);
					ops.add(theTransceiver.add(serialized, null, null, false));
				}
				try {
					return applyChanges(theTransceiver.applyOperations(ops)).throwIfError().getElement() != null;
				} catch (IOException e) {
					throw new UnsupportedOperationException(NO_CONNECTION, e);
				} catch (ConcurrentRemoteModException e) {
					applyChanges(e.getChanges());
					continue;
				}
			}
		}
	}

	@Override
	public void removeRange(int fromIndex, int toIndex) {
		if (fromIndex < 0 || fromIndex > toIndex)
			throw new IndexOutOfBoundsException(fromIndex + "..." + toIndex);
		try (Transaction t = lockLocal(true)) {
			if (toIndex > size())
				throw new IndexOutOfBoundsException(fromIndex + "..." + toIndex + " of " + size());
			if (fromIndex == toIndex)
				return;
			while (true) {
				CollectionElement<ValueElement> valueEl = theElements.getElement(fromIndex);
				int remaining = toIndex - fromIndex;
				List<O> ops = new ArrayList<>(toIndex - fromIndex);
				while (remaining > 0) {
					ops.add(theTransceiver.remove(valueEl.get().address));
					remaining--;
					valueEl = theElements.getAdjacentElement(valueEl.getElementId(), true);
				}
				try {
					applyChanges(theTransceiver.applyOperations(ops)).throwIfError();
				} catch (IOException e) {
					throw new UnsupportedOperationException(NO_CONNECTION, e);
				} catch (ConcurrentRemoteModException e) {
					applyChanges(e.getChanges());
					continue;
				}
			}
		}
	}

	@Override
	public boolean removeIf(Predicate<? super E> filter) {
		try (Transaction t = lockLocal(true)) {
			List<O> ops = new ArrayList<>();
			while (true) {
				for (ValueElement el : theElements) {
					if (filter.test(el.theValue))
						ops.add(theTransceiver.remove(el.address));
				}
				try {
					applyChanges(theTransceiver.applyOperations(ops)).throwIfError();
				} catch (IOException e) {
					throw new UnsupportedOperationException(NO_CONNECTION, e);
				} catch (ConcurrentRemoteModException e) {
					applyChanges(e.getChanges());
					continue;
				}
				return !ops.isEmpty();
			}
		}
	}

	@Override
	public boolean replaceAll(Function<? super E, ? extends E> map, boolean soft) {
		try (Transaction t = lockLocal(true)) {
			List<O> ops = new ArrayList<>();
			while (true) {
				for (ValueElement el : theElements) {
					E mapped = map.apply(el.theValue);
					if (mapped != el.theValue) {
						if (soft) {
							// This is expensive, but it's not a common use case and it's in the contract
							if (el.isAcceptable(mapped) != null)
								continue;
						}
						ops.add(theTransceiver.set(el.address, theSerializer.serializeValue(mapped)));
					}
				}
				try {
					applyChanges(theTransceiver.applyOperations(ops)).throwIfError();
				} catch (IOException e) {
					throw new UnsupportedOperationException(NO_CONNECTION, e);
				} catch (ConcurrentRemoteModException e) {
					applyChanges(e.getChanges());
					continue;
				}
				return !ops.isEmpty();
			}
		}
	}

	class ValueElement implements MutableCollectionElement<E> {
		final ElementId element;
		final ByteList address;
		E theValue;
		E oldValue;

		ValueElement(ByteList address, E value) {
			this.theValue = value;
			this.address = address;
			CollectionElement<ValueElement> newAfter = theElements.search(ve -> -ve.compareTo(address), SortedSearchFilter.Less);
			element = theObservableElements.addElement(this, newAfter == null ? null : newAfter.getElementId(), null, true).getElementId();
		}

		public int compareTo(ByteList addr) {
			int i;
			for (i = 0; i < address.size() && i < addr.size(); i++) {
				int comp = Byte.compare(address.get(i), addr.get(i));
				if (comp != 0)
					return comp;
			}
			return 0;
		}

		@Override
		public ElementId getElementId() {
			return element;
		}

		@Override
		public E get() {
			return theValue;
		}

		@Override
		public BetterCollection<E> getCollection() {
			return ClientCollection.this;
		}

		@Override
		public String isEnabled() {
			while (true) {
				try {
					try (Transaction t = lockLocal(false)) {
						return theTransceiver.queryCapability(Arrays.asList(theTransceiver.update(address)));
					}
				} catch (IOException e) {
					throw new UnsupportedOperationException(NO_CONNECTION, e);
				} catch (ConcurrentRemoteModException e) {
					applyChanges(e.getChanges());
				}
			}
		}

		@Override
		public String isAcceptable(E value) {
			VP serialized = null;
			while (true) {
				try {
					try (Transaction t = lockLocal(false)) {
						O op;
						if (value == theValue)
							op = theTransceiver.update(address);
						else {
							if (serialized == null)
								serialized = theSerializer.serializeValue(value);
							op = theTransceiver.set(address, serialized);
						}
						return theTransceiver.queryCapability(Arrays.asList(op));
					}
				} catch (IOException e) {
					throw new UnsupportedOperationException(NO_CONNECTION, e);
				} catch (ConcurrentRemoteModException e) {
					applyChanges(e.getChanges());
				}
			}
		}

		@Override
		public String canRemove() {
			while (true) {
				try {
					try (Transaction t = lockLocal(false)) {
						return theTransceiver.queryCapability(Arrays.asList(theTransceiver.remove(address)));
					}
				} catch (IOException e) {
					throw new UnsupportedOperationException(NO_CONNECTION, e);
				} catch (ConcurrentRemoteModException e) {
					applyChanges(e.getChanges());
				}
			}
		}

		@Override
		public void set(E value) throws UnsupportedOperationException, IllegalArgumentException {
			try (Transaction t = lockLocal(true)) {
				O op;
				if (value == theValue)
					op = theTransceiver.update(address);
				else
					op = theTransceiver.set(address, theSerializer.serializeValue(value));
				while (true) {
					try {
						applyChanges(theTransceiver.applyOperations(Arrays.asList(op))).throwIfError();
					} catch (IOException e) {
						throw new UnsupportedOperationException(NO_CONNECTION, e);
					} catch (ConcurrentRemoteModException e) {
						applyChanges(e.getChanges());
					}
				}
			}
		}

		@Override
		public void remove() throws UnsupportedOperationException {
			try (Transaction t = lockLocal(true)) {
				while (true) {
					try {
						applyChanges(theTransceiver.applyOperations(Arrays.asList(theTransceiver.remove(address)))).throwIfError();
					} catch (IOException e) {
						throw new UnsupportedOperationException(NO_CONNECTION, e);
					} catch (ConcurrentRemoteModException e) {
						applyChanges(e.getChanges());
					}
				}
			}
		}
	}
}
