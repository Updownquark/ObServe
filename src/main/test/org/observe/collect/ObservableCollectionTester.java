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

/**
 * A utility for testing an {@link ObservableCollection}
 *
 * @param <E> The type of values in the collection
 */
public class ObservableCollectionTester<E> extends AbstractObservableTester<Collection<E>> {
	private final ObservableCollection<E> theCollection;
	private final ArrayList<E> theSyncedCopy;
	private final ArrayList<E> theExpected;

	/** @param collect The observable collection to test */
	public ObservableCollectionTester(ObservableCollection<E> collect) {
		theCollection = collect;
		theSyncedCopy=new ArrayList<>();
		setSynced(true);
		theExpected = new ArrayList<>();
		theExpected.addAll(collect);
	}

	/** @return The expected values for the collection */
	public List<E> getExpected() {
		return theExpected;
	}

	/**
	 * @param values The values to set in the expected collection
	 * @return This tester
	 */
	public ObservableCollectionTester<E> set(E... values) {
		return set(Arrays.asList(values));
	}

	/**
	 * @param values The values to set in the expected collection
	 * @return This tester
	 */
	public ObservableCollectionTester<E> set(Collection<? extends E> values) {
		theExpected.clear();
		theExpected.addAll(values);
		return this;
	}

	/**
	 * @param values The values to add to the expected collection
	 * @return This tester
	 */
	public ObservableCollectionTester<E> add(E... values) {
		theExpected.addAll(Arrays.asList(values));
		return this;
	}

	/**
	 * @param values The values to remove from the expected collection
	 * @return This tester
	 */
	public ObservableCollectionTester<E> remove(E... values) {
		theExpected.removeAll(Arrays.asList(values));
		return this;
	}

	/**
	 * Removes all elements from the expected collection
	 *
	 * @return This tester
	 */
	public ObservableCollectionTester<E> clear() {
		theExpected.clear();
		return this;
	}

	/**
	 * @return The internal state that this tester maintains. Should always match the values of the observable collection when this tester
	 *         is {@link #setSynced(boolean) synced}.
	 */
	public List<E> getSyncedCopy(){
		return theSyncedCopy;
	}

	/**
	 * Checks this observable's value against its synced internal state and against the expected values in {@link #getExpected()}
	 */
	public void check() {
		check(theExpected);
	}

	/**
	 * Checks this observable's value against its synced internal state and against the expected values in {@link #getExpected()} and checks
	 * for the given number of operations
	 * 
	 * @param ops The number of operations expected to have occurred since the last check
	 */
	public void check(int ops) {
		check(theExpected, ops);
	}

	/**
	 * Checks this observable's value against its synced internal state and against the expected values in {@link #getExpected()} and checks
	 * for the given number of operations
	 * 
	 * @param minOps The minimum number of operations expected to have occurred since the last check
	 * @param maxOps The minimum number of operations expected to have occurred since the last check
	 */
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
