package org.observe.remote;

import java.util.AbstractCollection;
import java.util.Collection;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import org.observe.Subscription;
import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableCollectionEvent;
import org.qommons.collect.BetterSortedList.SortedSearchFilter;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.ElementId;
import org.qommons.tree.BetterTreeList;

public class ObservableCollectionServer<E, VP, CP> {
	public interface Client<P> {
		void eventOccurred(P event);
	}

	public interface ClientInterface<P> extends Subscription {
		boolean poll(Consumer<? super P> onEvent);

		void eventReceived(long eventId);
	}

	private final ObservableCollection<E> theCollection;
	private final CollectionConnectionSerializer<E, VP, CP> theSerializer;
	private final CollectionEventStore<CP> theEventStore;
	private final BetterTreeList<ClientHolder> theClients;
	private final BetterTreeList<CollectionServerElement> theElementRefs;
	private final AtomicLong theEventIdGen;

	private Subscription theCollectionSub;
	private Subscription theEventStoreSub;

	public ObservableCollectionServer(ObservableCollection<E> collection, CollectionConnectionSerializer<E, VP, CP> serializer,
		CollectionEventStore<CP> eventStore) {
		theCollection = collection;
		theSerializer = serializer;
		theEventStore = eventStore;

		theClients = BetterTreeList.<ClientHolder> build().build();
		theElementRefs = BetterTreeList.<CollectionServerElement> build().build();
		theEventIdGen = new AtomicLong(-1);
	}

	public CollectionConnectionSerializer<E, VP, CP> getSerializer() {
		return theSerializer;
	}

	public synchronized ClientInterface<CP> addClient(Client<? super CP> client, Consumer<? super Collection<VP>> onValue) {
		if (theClients.isEmpty())
			initListening();

		onValue.accept(new SerializedValues());
		ClientHolder holder = new ClientHolder(client, theEventIdGen.get());
		return holder;
	}

	public boolean isContentControlled() {
		return theCollection.isContentControlled();
	}

	public long getStamp() {
		return theEventIdGen.get();
	}

	// TODO Operations
	// Can add
	// Add
	// Can remove
	// Remove
	// Can set
	// Set
	// Can update
	// Update
	// lock/trylock

	private void initListening() {
		byte[] nextAddr = new byte[1];
		int size = theCollection.size();
		byte addrIncrement = (byte) Math.max(1, 256 / size);
		nextAddr[0] = (byte) (0x80 + addrIncrement / 2);
		for (CollectionElement<E> element : theCollection.elements()) {
			CollectionElement<CollectionServerElement> serverEl = theElementRefs.addElement(null, false);
			theElementRefs.mutableElement(serverEl.getElementId()).set(//
				new CollectionServerElement(element.getElementId(), serverEl.getElementId(), nextAddr));
			byte lastEl = nextAddr[nextAddr.length - 1];
			byte nextAddrEl = (byte) (lastEl + addrIncrement);
			if (nextAddrEl < 0 && lastEl >= 0 && theCollection.getAdjacentElement(element.getElementId(), true) != null) {
				byte[] newNextAddr = new byte[nextAddr.length + 1];
				System.arraycopy(nextAddr, 0, newNextAddr, 0, nextAddr.length);
				size /= 256;
				addrIncrement = (byte) Math.max(1, 256 / size);
				newNextAddr[nextAddr.length] = (byte) (0x80 + addrIncrement / 2);
				nextAddr = newNextAddr;
			}
		}
		theEventStoreSub = theEventStore.addListener(new CollectionEventStore.EventStoreListener() {
			@Override
			public void eventsPurged(long oldestRemaining) {
				for (CollectionElement<ClientHolder> c : theClients.elements()) {
					if (c.get().theLastReceivedEvent >= oldestRemaining)
						break;
					else
						theClients.mutableElement(c.getElementId()).remove();
				}
			}
		});
		theCollectionSub = theCollection.onChange(evt -> {
			fireChange(evt, evt.getRootCausable().isFinished());
		});
	}

	synchronized void fireChange(ObservableCollectionEvent<? extends E> event, boolean transactionEnd) {
		long eventId = theEventIdGen.incrementAndGet();
		CollectionElement<CollectionServerElement> serverEl = null;
		switch (event.getType()) {
		case add:
			CollectionServerElement left = CollectionElement.get(//
				theElementRefs.search(cse -> event.getElementId().compareTo(cse.collectionEl), SortedSearchFilter.Less));
			CollectionServerElement right = CollectionElement.get(//
				left == null ? theElementRefs.getTerminalElement(true) : theElementRefs.getAdjacentElement(left.refEl, true));
			serverEl = theElementRefs.addElement(null, left == null ? null : left.refEl, right == null ? null : right.refEl, false);
			theElementRefs.mutableElement(serverEl.getElementId()).set(//
				new CollectionServerElement(event.getElementId(), serverEl.getElementId()));
			break;
		case remove:
			serverEl = theElementRefs.search(cse -> event.getElementId().compareTo(cse.collectionEl), SortedSearchFilter.OnlyMatch);
			theElementRefs.mutableElement(serverEl.getElementId()).remove();
			break;
		case set:
			serverEl = theElementRefs.search(cse -> event.getElementId().compareTo(cse.collectionEl), SortedSearchFilter.OnlyMatch);
			break;
		}
		CP persisted = theSerializer.serializeChange(new CollectionConnectionSerializer.SerializedCollectionChange<>(eventId,
			new ByteAddress(serverEl.get().address), event.getType(), event.getOldValue(), event.getNewValue(),
			transactionEnd));
		// Make a copy, because the clients could re-order themselves (by calling eventReceived) from the eventOccurred method
		ClientHolder[] clients = theClients.toArray(new ObservableCollectionServer.ClientHolder[theClients.size()]);
		for (ClientHolder client : clients) {
			client.client.eventOccurred(persisted);
		}
		if (!theClients.isEmpty() && theClients.getTerminalElement(true).get().theLastReceivedEvent < eventId)
			theEventStore.store(eventId, persisted);
	}

	class ClientHolder implements ClientInterface<CP> {
		final Client<? super CP> client;
		ElementId theElement;
		long theLastReceivedEvent;

		ClientHolder(Client<? super CP> client, long lastReceivedEvent) {
			this.client = client;
			theLastReceivedEvent = lastReceivedEvent;
			theElement = theClients.addElement(this, false).getElementId();
		}

		@Override
		public void unsubscribe() {
			synchronized (ObservableCollectionServer.this) {
				if (!theElement.isPresent())
					return;
				if (theClients.getAdjacentElement(theElement, false) == null) {
					CollectionElement<ClientHolder> next = theClients.getAdjacentElement(theElement, true);
					long purgeId = next == null ? theEventIdGen.get() : next.get().theLastReceivedEvent;
					if (purgeId > theLastReceivedEvent)
						theEventStore.release(purgeId);
				}
				theClients.mutableElement(theElement).remove();

				if (theClients.isEmpty()) {
					theCollectionSub.unsubscribe();
					theEventStoreSub.unsubscribe();
					theCollectionSub = theEventStoreSub = null;
					theElementRefs.clear();
				}
			}
		}

		@Override
		public boolean poll(Consumer<? super CP> onEvent) {
			synchronized (ObservableCollectionServer.this) {
				if (!theElement.isPresent())
					return false;
				long eventId = theLastReceivedEvent + 1;
				long lastEventId = theEventIdGen.get();
				while (eventId <= lastEventId) {
					CP persisted = theEventStore.retrieve(eventId);
					onEvent.accept(persisted);
					eventId++;
				}
				return true;
			}
		}

		@Override
		public void eventReceived(long eventId) {
			synchronized (ObservableCollectionServer.this) {
				if (eventId <= theLastReceivedEvent)
					return;
				theLastReceivedEvent = eventId;
				CollectionElement<ClientHolder> next = theClients.getAdjacentElement(theElement, true);
				if (next == null || next.get().theLastReceivedEvent >= eventId)
					theEventStore.release(eventId);
				else {
					theEventStore.release(next.get().theLastReceivedEvent);
					theClients.mutableElement(theElement).remove();
					while (next != null && next.get().theLastReceivedEvent > eventId)
						next = theClients.getAdjacentElement(next.getElementId(), true);
					if (next != null)
						theElement = theClients.addElement(this, null, next.getElementId(), false).getElementId();
					else
						theElement = theClients.addElement(this, false).getElementId();
				}
			}
		}
	}

	class CollectionServerElement implements Comparable<CollectionServerElement> {
		final ElementId collectionEl;
		final ElementId refEl;
		final byte[] address;

		CollectionServerElement(ElementId collectionEl, ElementId refEl) {
			this.collectionEl = collectionEl;
			this.refEl = refEl;
			CollectionServerElement left = CollectionElement.get(theElementRefs.getAdjacentElement(refEl, false));
			CollectionServerElement right = CollectionElement.get(theElementRefs.getAdjacentElement(refEl, true));
			address = addressBetween(left == null ? null : left.address, right == null ? null : right.address);
		}

		CollectionServerElement(ElementId collectionEl, ElementId refEl, byte[] address) {
			this.collectionEl = collectionEl;
			this.refEl = refEl;
			this.address = address;
		}

		@Override
		public int compareTo(CollectionServerElement o) {
			int i;
			for (i = 0; i < address.length && i < o.address.length; i++) {
				int comp = Byte.compare(address[i], o.address[i]);
				if (comp != 0)
					return comp;
			}
			if (i < address.length)
				return 1;
			else if (i < o.address.length)
				return -1;
			else
				return 0;
		}
	}

	static byte[] addressBetween(byte[] left, byte[] right) {
		int differ;
		byte lastElement;
		if (left != null) {
			if (right != null) {
				differ = 0;
				while (differ < left.length && differ < right.length && left[differ] == right[differ])
					differ++;
				if (differ < left.length) {
					if (differ < right.length) {
						if (left[differ] == right[differ] - 1) {
							differ++;
							lastElement = 0;
						} else
							lastElement = (byte) ((left[differ] + right[differ]) >> 1);
					} else
						lastElement = (byte) ((left[differ] + 0x7f) >> 1);
				} else if (differ < right.length) {
					lastElement = (byte) ((0x80 + right[differ]) >> 1);
				} else {
					lastElement = 0;
				}
			} else {
				differ = 0;
				while (differ < left.length && left[differ] == 0x7f)
					differ++;
				lastElement = 0;
			}
		} else if (right != null) {
			differ = 0;
			while (differ < right.length && right[differ] == 0x80)
				differ++;
			lastElement = 0;
		} else {
			differ = 0;
			lastElement = 0;
		}
		byte[] addr = new byte[differ + 1];
		System.arraycopy(left, 0, addr, 0, differ);
		addr[differ] = lastElement;
		return addr;
	}

	class SerializedValues extends AbstractCollection<VP> {
		@Override
		public int size() {
			return theCollection.size();
		}

		@Override
		public Iterator<VP> iterator() {
			return new Iterator<VP>() {
				private final Iterator<E> theValueIter = theCollection.iterator();

				@Override
				public boolean hasNext() {
					return theValueIter.hasNext();
				}

				@Override
				public VP next() {
					return theSerializer.serializeValue(theValueIter.next());
				}
			};
		}
	}
}
