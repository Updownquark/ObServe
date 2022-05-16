package org.observe.util.swing;

import java.awt.EventQueue;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import javax.swing.ListModel;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;

import org.observe.Subscription;
import org.observe.collect.CollectionChangeEvent;
import org.observe.collect.CollectionChangeType;
import org.observe.collect.ObservableCollection;
import org.observe.dbug.Dbug;
import org.observe.dbug.DbugAnchor;
import org.observe.dbug.DbugAnchorType;
import org.observe.util.TypeTokens;
import org.qommons.Causable;
import org.qommons.Transaction;
import org.qommons.collect.BetterList;
import org.qommons.tree.BetterTreeList;

import com.google.common.reflect.TypeToken;

/**
 * A swing ListModel backed by an {@link ObservableCollection}
 *
 * @param <E> The type of data in the collection
 */
public class ObservableListModel<E> implements ListModel<E> {
	/** Anchor type for {@link Dbug}-based debugging */
	@SuppressWarnings("rawtypes")
	public static final DbugAnchorType<ObservableListModel> DBUG = Dbug.common().anchor(ObservableListModel.class, a -> a//
		.withField("type", true, false, TypeTokens.get().keyFor(TypeToken.class).wildCard())//
		.withEvent("add").withEvent("remove").withEvent("set"));

	@SuppressWarnings("rawtypes")
	private final DbugAnchor<ObservableListModel> theAnchor;
	private final ObservableCollection<E> theWrapped;
	/**
	 * This model must keep an independent representation of its data, which is only modified on the EDT, just before firing an event
	 * documenting the modification
	 */
	private final List<E> theCachedData;
	private final List<ListDataListener> theListeners;
	private Subscription theListening;
	private volatile boolean isEventing;

	/** @param wrap The observable collection to back this model */
	public ObservableListModel(ObservableCollection<E> wrap) {
		theAnchor = DBUG.instance(this, a -> a//
			.setField("type", wrap.getType(), null)//
			);
		if (wrap == null)
			throw new NullPointerException();
		theWrapped = wrap;
		theCachedData = new ArrayList<>();
		theListeners = BetterTreeList.<ListDataListener> build().build();
	}

	/** @return The observable list that this model wraps */
	public ObservableCollection<E> getWrapped() {
		return theWrapped;
	}

	@Override
	public int getSize() {
		if (theListening != null)
			return theCachedData.size();
		else
			return theWrapped.size();
	}

	@Override
	public E getElementAt(int index) {
		if (theListening != null)
			return theCachedData.get(index);
		else
			return theWrapped.get(index);
	}

	@Override
	public void addListDataListener(ListDataListener l) {
		ObservableSwingUtils.onEQ(() -> {
			if (theListeners.isEmpty())
				beginListening();
			theListeners.add(l);
		});
	}

	@Override
	public void removeListDataListener(ListDataListener l) {
		ObservableSwingUtils.onEQ(() -> {
			theListeners.remove(l);
			if (theListeners.isEmpty() && theListening != null) {
				theListening.unsubscribe();
				theListening = null;
				if (isEventing)
					EventQueue.invokeLater(() -> theCachedData.clear());
				else
					theCachedData.clear();
			}
		});
	}

	private void beginListening() {
		try (Transaction t = theWrapped.lock(false, null)) {
			theCachedData.addAll(theWrapped);
			theListening = theWrapped.changes().act(this::handleEvent);
		}
	}

	private void handleEvent(CollectionChangeEvent<E> event) {
		isEventing = true;
		try {
			Map<Integer, E> changesByIndex = new HashMap<>();
			if (event.type != CollectionChangeType.remove) {
				for (CollectionChangeEvent.ElementChange<E> el : event.elements)
					changesByIndex.put(el.index, el.newValue);
			}
			int[][] split = ObservableSwingUtils.getContinuousIntervals(event.elements, event.type != CollectionChangeType.remove);
			for (int[] indexes : split) {
				CausableListEvent wrappedEvent = new CausableListEvent(ObservableListModel.this, getSwingType(event.type), indexes[0],
					indexes[1], event);
				try (Transaction t = wrappedEvent.use()) {
					switch (event.type) {
					case add:
						theAnchor.event("add", event);
						for (int i = indexes[0]; i <= indexes[1]; i++)
							theCachedData.add(i, changesByIndex.remove(i));
						intervalAdded(wrappedEvent);
						break;
					case remove:
						theAnchor.event("remove", event);
						for (int i = indexes[1]; i >= indexes[0]; i--)
							theCachedData.remove(i);
						intervalRemoved(wrappedEvent);
						break;
					case set:
						theAnchor.event("set", event);
						for (int i = indexes[0]; i <= indexes[1]; i++)
							theCachedData.set(i, changesByIndex.remove(i));
						contentsChanged(wrappedEvent);
						break;
					}
				}
			}
		} finally {
			isEventing = false;
		}
	}

	private void intervalAdded(ListDataEvent event) {
		for (ListDataListener listener : theListeners) {
			try {
				listener.intervalAdded(event);
			} catch (RuntimeException e) {
				e.printStackTrace();
			}
		}
	}

	private void intervalRemoved(ListDataEvent event) {
		for (ListDataListener listener : theListeners) {
			try {
				listener.intervalRemoved(event);
			} catch (RuntimeException e) {
				e.printStackTrace();
			}
		}
	}

	private void contentsChanged(ListDataEvent event) {
		for (ListDataListener listener : theListeners) {
			try {
				listener.contentsChanged(event);
			} catch (RuntimeException e) {
				e.printStackTrace();
			}
		}
	}

	private static int getSwingType(CollectionChangeType type) {
		switch(type){
		case add:
			return ListDataEvent.INTERVAL_ADDED;
		case remove:
			return ListDataEvent.INTERVAL_REMOVED;
		case set:
			return ListDataEvent.CONTENTS_CHANGED;
		}
		throw new IllegalStateException("Unrecognized event type: " + type);
	}

	/** Allows causable-aware listeners to this class to inspect the causality chain */
	static class CausableListEvent extends ListDataEvent implements Causable {
		private final BetterList<Object> theCauses;
		private final Causable theRootCausable;
		private LinkedHashMap<CausableKey, Supplier<Transaction>> theKeys;
		private boolean isStarted;
		private boolean isFinished;
		private boolean isTerminated;

		public CausableListEvent(Object source, int type, int index0, int index1, Object... causes) {
			super(source, type, index0, index1);
			theCauses = BetterList.of(causes);
			if (causes == null)
				theRootCausable = this;
			else {
				Causable root = this;
				for (Object cause : causes) {
					if (cause instanceof Causable && !(cause instanceof ChainBreak)) {
						if (((Causable) cause).isTerminated())
							throw new IllegalStateException("Cannot use a finished Causable as a cause");
						root = ((Causable) cause).getRootCausable();
						if (root.isTerminated())
							throw new IllegalStateException("Cannot use a finished Causable as a cause");
						break;
					}
				}
				theRootCausable = root;
			}
		}

		@Override
		public BetterList<Object> getCauses() {
			return theCauses;
		}

		@Override
		public Causable getRootCausable() {
			return theRootCausable;
		}

		@Override
		public Map<Object, Object> onFinish(CausableKey key) {
			if (!isStarted)
				throw new IllegalStateException("Not started!  Use Causable.use(Causable)");
			else if (isTerminated)
				throw new IllegalStateException("This cause has already terminated");
			if (theKeys == null)
				theKeys = new LinkedHashMap<>();
			theKeys.computeIfAbsent(key, k -> k.use(this));
			return key.getData();
		}

		@Override
		public boolean isFinished() {
			return isFinished;
		}

		@Override
		public boolean isTerminated() {
			return isTerminated;
		}

		private void finish() {
			if (!isStarted)
				throw new IllegalStateException("Not started!  Use Causable.use(Causable)");
			if (isFinished)
				throw new IllegalStateException("A cause may only be finished once");
			isFinished = true;
			// The finish actions may use this causable as a cause for events they fire.
			// These events may trigger onRootFinish calls, which add more actions to this causable
			// Though this cycle is allowed, care must be taken by callers to ensure it does not become infinite
			try {
				if (theKeys != null) {
					while (!theKeys.isEmpty()) {
						LinkedList<Transaction> postActions = null;
						while (!theKeys.isEmpty()) {
							Iterator<Supplier<Transaction>> keyActionIter = theKeys.values().iterator();
							Supplier<Transaction> keyAction = keyActionIter.next();
							keyActionIter.remove();
							Transaction postAction = keyAction.get();
							if (postAction != null) {
								if (postActions == null)
									postActions = new LinkedList<>();
								postActions.addFirst(postAction);
							}
						}
						if (postActions != null) {
							for (Transaction key : postActions)
								key.close();
							postActions.clear();
						}
					}
				}
			} finally {
				isTerminated = true;
			}
		}

		@Override
		public Transaction use() {
			if (isStarted)
				throw new IllegalStateException("This causable is already being (or has been) used");
			isStarted = true;
			return this::finish;
		}
	}
}
