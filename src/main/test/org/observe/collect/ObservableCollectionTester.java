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
	private final String theName;
	private final ObservableCollection<? extends E> theCollection;
	private final ArrayList<E> theSyncedCopy;
	private final ArrayList<E> theBatchSyncedCopy;
	private final List<E> theExpected;

	/** @param collect The observable collection to test */
	public ObservableCollectionTester(String name, ObservableCollection<? extends E> collect) {
		this(name, collect, new ArrayList<>());
	}

	/**
	 * @param collect The observable collection to test
	 * @param expected The collection to use for the expected value
	 */
	public ObservableCollectionTester(String name, ObservableCollection<? extends E> collect, List<E> expected) {
		theName = name;
		theCollection = collect;
		theSyncedCopy=new ArrayList<>();
		theBatchSyncedCopy = new ArrayList<>();
		setSynced(true);
		theExpected = expected;
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
	 * @param index The index to add the value at
	 * @param value The value to add
	 * @return This tester
	 */
	public ObservableCollectionTester<E> add(int index, E value) {
		theExpected.add(index, value);
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
	 * @param values The values to add to the expected collection
	 * @return This tester
	 */
	public ObservableCollectionTester<E> addAll(Collection<? extends E> values) {
		theExpected.addAll(values);
		return this;
	}

	/**
	 * @param value The value to remove from the expected collection
	 * @return This tester
	 */
	public ObservableCollectionTester<E> remove(E value) {
		theExpected.remove(value);
		return this;
	}

	/**
	 * @param values The values to remove from the expected collection
	 * @return This tester
	 */
	public ObservableCollectionTester<E> removeAll(E... values) {
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
		assertThat(theSyncedCopy, QommonsTestUtils.collectionsEqual(expected, true));
		assertThat(theBatchSyncedCopy, QommonsTestUtils.collectionsEqual(expected, true));
	}

	@Override
	public void checkSynced() {
		assertThat(theSyncedCopy, QommonsTestUtils.collectionsEqual(theCollection, true));
		assertThat(theBatchSyncedCopy, QommonsTestUtils.collectionsEqual(theCollection, true));
	}

	/** Checks the non-batched synchronized collection against the source collection and the internal expected values */
	public void checkNonBatchSynced() {
		checkNonBatchSynced(theExpected);
	}

	/**
	 * Checks the non-batched synchronized collection against the source collection and the given expected values
	 *
	 * @param expected The expected values
	 */
	public void checkNonBatchSynced(Collection<E> expected) {
		assertThat(theSyncedCopy, QommonsTestUtils.collectionsEqual(theCollection, true));
		assertThat(theSyncedCopy, QommonsTestUtils.collectionsEqual(expected, true));
	}

	@Override
	protected Subscription sync() {
		Subscription singleSub = theCollection.subscribe(evt -> {
			switch (evt.getType()) {
			case add:
				theSyncedCopy.add(evt.getIndex(), evt.getNewValue());
				break;
			case remove:
				assertEquals(theName, evt.getOldValue(), theSyncedCopy.remove(evt.getIndex()));
				break;
			case set:
				assertEquals(theName, evt.getOldValue(), theSyncedCopy.set(evt.getIndex(), evt.getNewValue()));
				break;
			}
		}, true);
		theBatchSyncedCopy.addAll(theCollection); // The changes observable doesn't populate initial values
		Subscription batchSub = theCollection.changes().act(evt -> {
			op();
			switch (evt.type) {
			case add:
				for (CollectionChangeEvent.ElementChange<? extends E> change : evt.elements)
					theBatchSyncedCopy.add(change.index, change.newValue);
				break;
			case remove:
				for (CollectionChangeEvent.ElementChange<? extends E> change : evt.getElementsReversed())
					assertEquals(theName, change.oldValue, theBatchSyncedCopy.remove(change.index));
				break;
			case set:
				for (CollectionChangeEvent.ElementChange<? extends E> change : evt.elements)
					assertEquals(theName, change.oldValue, theBatchSyncedCopy.set(change.index, change.newValue));
				break;
			}
		});
		return () -> {
			singleSub.unsubscribe();
			batchSub.unsubscribe();
		};
	}
}
