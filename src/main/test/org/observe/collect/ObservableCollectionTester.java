package org.observe.collect;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.observe.AbstractObservableTester;
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
		assertThat(theExpected, QommonsTestUtils.collectionsEqual(theSyncedCopy, true));
	}

	@Override
	public void checkSynced() {
		assertThat(theSyncedCopy, QommonsTestUtils.collectionsEqual(theCollection, true));
	}

	@Override
	protected Subscription sync() {
		return theCollection.subscribe(evt -> {
			op();
			switch (evt.getType()) {
			case add:
				theSyncedCopy.add(evt.getIndex(), evt.getNewValue());
				break;
			case remove:
				assertEquals(evt.getOldValue(), theSyncedCopy.remove(evt.getIndex()));
				break;
			case set:
				assertEquals(evt.getOldValue(), theSyncedCopy.set(evt.getIndex(), evt.getNewValue()));
				break;
			}
		}, true);
	}
}
