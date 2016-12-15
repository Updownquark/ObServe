package org.observe.collect;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

import org.observe.AbstractObservableTester;
import org.observe.ObservableValueEvent;
import org.observe.Observer;
import org.observe.Subscription;
import org.qommons.QommonsTestUtils;

public class ObservableCollectionTester<E> extends AbstractObservableTester<Collection<E>> {
	private final ObservableCollection<E> theCollection;
	private final ArrayList<E> theSyncedCopy;
	private final ArrayList<E> theExpected;

	public ObservableCollectionTester(ObservableCollection<E> collect) {
		theCollection = collect;
		theSyncedCopy=new ArrayList<>();
		setSynced(true);
		theExpected = new ArrayList<>();
		theExpected.addAll(collect);
	}

	public List<E> getExpected() {
		return theExpected;
	}

	public ObservableCollectionTester<E> set(E... values) {
		return set(Arrays.asList(values));
	}

	public ObservableCollectionTester<E> set(Collection<? extends E> values) {
		theExpected.clear();
		theExpected.addAll(values);
		return this;
	}

	public ObservableCollectionTester<E> add(E... values) {
		theExpected.addAll(Arrays.asList(values));
		return this;
	}

	public ObservableCollectionTester<E> remove(E... values) {
		theExpected.removeAll(Arrays.asList(values));
		return this;
	}

	public ObservableCollectionTester<E> clear() {
		theExpected.clear();
		return this;
	}

	public List<E> getSyncedCopy(){
		return theSyncedCopy;
	}

	public void check() {
		check(theExpected);
	}

	public void check(int ops) {
		check(theExpected, ops);
	}

	public void check(int minOps, int maxOps) {
		check(theExpected, minOps, maxOps);
	}

	@Override
	public void checkValue(Collection<E> expected) {
		boolean ordered = theCollection instanceof ObservableOrderedCollection;
		assertThat(theExpected, QommonsTestUtils.collectionsEqual(theSyncedCopy, ordered));
	}

	@Override
	public void checkSynced() {
		boolean ordered = theCollection instanceof ObservableOrderedCollection;
		assertThat(theSyncedCopy, QommonsTestUtils.collectionsEqual(theCollection, ordered));
	}

	@Override
	protected Subscription sync() {
		if (theCollection instanceof ObservableOrderedCollection)
			return ((ObservableOrderedCollection<E>) theCollection).onOrderedElement(new Consumer<ObservableOrderedElement<E>>() {
				@Override
				public void accept(ObservableOrderedElement<E> el) {
					el.subscribe(new Observer<ObservableValueEvent<E>>() {
						@Override
						public <V extends ObservableValueEvent<E>> void onNext(V evt) {
							op();
							if (evt.isInitial())
								theSyncedCopy.add(el.getIndex(), evt.getValue());
							else {
								assertEquals(evt.getOldValue(), theSyncedCopy.get(el.getIndex()));
								theSyncedCopy.set(el.getIndex(), evt.getValue());
							}
						}

						@Override
						public <V extends ObservableValueEvent<E>> void onCompleted(V evt) {
							op();
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
							op();
							if (evt.isInitial())
								theSyncedCopy.add(evt.getValue());
							else {
								assertTrue(theSyncedCopy.remove(evt.getOldValue()));
								theSyncedCopy.add(evt.getValue());
							}
						}

						@Override
						public <V extends ObservableValueEvent<E>> void onCompleted(V evt) {
							op();
							assertTrue(theSyncedCopy.remove(evt.getValue()));
						}
					});
				}
			});
	}
}
