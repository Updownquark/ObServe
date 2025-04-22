package org.observe.util;

import java.util.function.Consumer;

import org.observe.Subscription;
import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableCollectionEvent;
import org.qommons.LongList;
import org.qommons.Stamped;
import org.qommons.Transaction;

/**
 * A wrapper for an {@link ObservableCollection} whose values are {@link Stamped}. The value added here is that changes to the underlying
 * collection are swallowed by this collection if the stamped value in the change event has not actually changed (i.e. its stamp is not
 * updated).
 *
 * @param <E> The type of the collection
 */
public class StampedUpdateFilteredCollection<E extends Stamped> extends ObservableCollectionWrapper<E> {
	private long theCachedContainerStamp;
	private long theValueStamp;
	private long thePublishedStamp;

	/** @param toWrap The collection of stamped values to wrap */
	public StampedUpdateFilteredCollection(ObservableCollection<E> toWrap) {
		init(toWrap);
		theCachedContainerStamp = theValueStamp = -1;
	}

	@Override
	public long getStamp() {
		long wrapperStamp = super.getStamp();
		if (theCachedContainerStamp != -1 && theCachedContainerStamp == wrapperStamp)
			return thePublishedStamp;
		theCachedContainerStamp = wrapperStamp;
		// We need show up as changed when any values have been swapped for others that may have the same stamp
		long valueStamp = Stamped.compositeStamp(getWrapped(), v -> (v == null ? 0 : (System.identityHashCode(v) ^ v.getStamp())));
		if (theValueStamp != -1 && theValueStamp == valueStamp)
			return thePublishedStamp;
		theValueStamp = valueStamp;
		thePublishedStamp = wrapperStamp;
		return wrapperStamp;
	}

	@Override
	public Subscription onChange(Consumer<? super ObservableCollectionEvent<? extends E>> observer) {
		LongList stamps = new LongList(size());
		try (Transaction t = lock(false, null)) {
			for (E value : this)
				stamps.add(value == null ? 0 : value.getStamp());
			return super.onChange(evt -> {
				switch (evt.getType()) {
				case add:
					stamps.add(evt.getIndex(), evt.getNewValue() == null ? 0 : evt.getNewValue().getStamp());
					observer.accept(evt);
					break;
				case remove:
					stamps.remove(evt.getIndex());
					observer.accept(evt);
					break;
				case set:
					long newStamp = evt.getNewValue() == null ? 0 : evt.getNewValue().getStamp();
					long oldStamp = stamps.set(evt.getIndex(), newStamp);
					if (!evt.isUpdate() || oldStamp != newStamp)
						observer.accept(evt);
				}
			});
		}
	}
}
