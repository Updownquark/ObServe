package org.observe.remote;

import java.io.IOException;
import java.util.Collection;
import java.util.List;

import org.observe.collect.CollectionChangeType;
import org.qommons.Transaction;
import org.qommons.collect.MutableCollectionElement.StdMsg;

public interface CollectionClientTransceiver<P, O> {
	public class SerializedCollectionChange<P> {
		public final long stamp;
		public final ByteAddress elementId;
		public final CollectionChangeType type;
		public final P oldValue;
		public final P newValue;

		public SerializedCollectionChange(long stamp, ByteAddress elementId, CollectionChangeType type, P oldValue, P newValue) {
			this.stamp = stamp;
			this.elementId = elementId;
			this.type = type;
			this.oldValue = oldValue;
			this.newValue = newValue;
		}
	}

	public class CollectionPollResult<P> {
		public final Collection<SerializedCollectionChange<P>> changes;

		public CollectionPollResult(Collection<SerializedCollectionChange<P>> changes) {
			this.changes = changes;
		}
	}

	public class OperationResultSet<P> extends CollectionPollResult<P> {
		private final ByteAddress theElement;
		private final String theError;

		public OperationResultSet(Collection<SerializedCollectionChange<P>> changes, ByteAddress element, String error) {
			super(changes);
			theElement = element;
			theError = error;
		}

		public ByteAddress getElement() {
			return theElement;
		}

		public String getError() {
			return theError;
		}

		public OperationResultSet<P> throwIfError() throws UnsupportedOperationException, IllegalArgumentException {
			if (theError == null)
				return this;
			else if (theError == StdMsg.UNSUPPORTED_OPERATION)
				throw new UnsupportedOperationException(theError);
			else
				throw new IllegalArgumentException(theError);
		}
	}

	public class ConcurrentRemoteModException extends Exception {
		private final CollectionPollResult<?> theChanges;

		public <P> ConcurrentRemoteModException(Collection<SerializedCollectionChange<P>> changes) {
			theChanges = new CollectionPollResult<>(changes);
		}

		public <P> CollectionPollResult<P> getChanges() {
			return (CollectionPollResult<P>) theChanges;
		}
	}

	public class LockResult<P> extends CollectionPollResult<P> {
		private final Transaction theTransaction;

		public LockResult(Collection<SerializedCollectionChange<P>> changes, Transaction transaction) {
			super(changes);
			theTransaction = transaction;
		}

		public Transaction get() {
			return theTransaction;
		}
	}

	boolean isContentControlled() throws IOException;

	long getLastChange();
	void setLastChange(long changeId);

	LockResult<P> lock(boolean write) throws IOException;
	LockResult<P> tryLock(boolean write) throws IOException;

	CollectionPollResult<P> poll() throws IOException;

	O add(P value, ByteAddress after, ByteAddress before, boolean first);

	O remove(ByteAddress element);

	O set(ByteAddress element, P value);

	O update(ByteAddress element);

	String queryCapability(List<O> operations) throws IOException, ConcurrentRemoteModException;
	OperationResultSet<P> applyOperations(List<O> operations) throws IOException, ConcurrentRemoteModException;
}
