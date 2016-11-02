package org.observe.collect;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

import org.observe.ObservableValueEvent;
import org.observe.Observer;
import org.observe.Subscription;
import org.qommons.QommonsTestUtils;

public class ObservableCollectionTester<E> extends ArrayList<E> {
	private final ObservableCollection<E> theCollection;
	private final ArrayList<E> theSyncedCopy;
	private Subscription theSyncSubscription;
	private int theOldOpCount;
	private int theOpCount;

	public ObservableCollectionTester(ObservableCollection<E> collect) {
		theCollection = collect;
		addAll(collect);
		theSyncedCopy=new ArrayList<>();
		theSyncSubscription= sync();
	}

	public ObservableCollectionTester<E> set(Collection<? extends E> values) {
		clear();
		addAll(values);
		return this;
	}

	public List<E> getSyncedCopy(){
		return theSyncedCopy;
	}

	public void check() {
		check(0, 0);
	}

	public void check(int ops) {
		check(ops, ops);
	}

	public void check(int minOps, int maxOps) {
		checkOps(minOps, maxOps);
		checkSynced();
		boolean ordered = theCollection instanceof ObservableOrderedCollection;
		assertThat(this, QommonsTestUtils.collectionsEqual(theSyncedCopy, ordered));
	}

	public void checkSynced() {
		boolean ordered = theCollection instanceof ObservableOrderedCollection;
		assertThat(theSyncedCopy, QommonsTestUtils.collectionsEqual(theCollection, ordered));
	}

	public void checkOps(int ops) {
		checkOps(ops, ops);
	}

	public void checkOps(int minOps, int maxOps) {
		int ops = theOpCount - theOldOpCount;
		if (minOps == maxOps && maxOps > 0)
			assertEquals(minOps, ops);
		else {
			if (minOps > 0)
				assertTrue("Expected at least " + minOps + " operations, got " + ops, ops >= minOps);
			else if (maxOps > 0 && maxOps < Integer.MAX_VALUE)
				assertTrue("Expected at most " + maxOps + " operations, got " + ops, ops <= maxOps);
		}
		theOldOpCount = theOpCount;
	}

	public void setSynced(boolean synced){
		if(synced==(theSyncSubscription!=null))
			return;
		if(synced)
			theSyncSubscription=sync();
		else{
			theSyncSubscription.unsubscribe();
			theSyncSubscription=null;
		}
	}

	private Subscription sync() {
		if (theCollection instanceof ObservableOrderedCollection)
			return ((ObservableOrderedCollection<E>) theCollection).onOrderedElement(new Consumer<ObservableOrderedElement<E>>() {
				@Override
				public void accept(ObservableOrderedElement<E> el) {
					el.subscribe(new Observer<ObservableValueEvent<E>>() {
						@Override
						public <V extends ObservableValueEvent<E>> void onNext(V evt) {
							theOpCount++;
							if (evt.isInitial())
								theSyncedCopy.add(el.getIndex(), evt.getValue());
							else {
								assertEquals(evt.getOldValue(), theSyncedCopy.get(el.getIndex()));
								theSyncedCopy.set(el.getIndex(), evt.getValue());
							}
						}

						@Override
						public <V extends ObservableValueEvent<E>> void onCompleted(V evt) {
							theOpCount++;
							assertEquals(evt.getValue(), theSyncedCopy.remove(el.getIndex()));
						}
					});
				}
			});
		else
			return theCollection.onElement(new Consumer<ObservableElement<E>>() {
				@Override
				public void accept(ObservableElement<E> el) {
					el.subscribe(new Observer<ObservableValueEvent<E>>() {
						@Override
						public <V extends ObservableValueEvent<E>> void onNext(V evt) {
							theOpCount++;
							if (evt.isInitial())
								theSyncedCopy.add(evt.getValue());
							else {
								assertTrue(theSyncedCopy.remove(evt.getOldValue()));
								theSyncedCopy.add(evt.getValue());
							}
						}

						@Override
						public <V extends ObservableValueEvent<E>> void onCompleted(V evt) {
							theOpCount++;
							assertTrue(theSyncedCopy.remove(evt.getValue()));
						}
					});
				}
			});
	}
}
