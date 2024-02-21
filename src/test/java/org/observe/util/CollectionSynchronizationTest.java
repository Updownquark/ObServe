package org.observe.util;

import java.time.Duration;

import org.junit.Assert;
import org.junit.Test;
import org.observe.collect.ObservableCollection;
import org.observe.util.ObservableCollectionSynchronization.Builder;
import org.qommons.testing.QommonsTestUtils;
import org.qommons.testing.TestHelper;
import org.qommons.testing.TestHelper.Testable;

/** Tests {@link ObservableCollectionSynchronization} */
public class CollectionSynchronizationTest {
	/** Simple, {@link ObservableCollectionSynchronization.Builder#strictOrder() strict-ordered} test */
	@Test
	public void simpleTest() {
		TestHelper.createTester(SimpleTest.class)//
		.withFailurePersistence(true).revisitKnownFailures(true).withDebug(true)//
		.withMaxCaseDuration(Duration.ofSeconds(1)).withRandomCases(200)//
		// .withConcurrency(p -> p - 2)//
		.withPlacemarks("op")//
		.execute()//
		.throwErrorIfFailed();
	}

	/** Simple, {@link ObservableCollectionSynchronization.Builder#preferOrdered() preferred-order} test */
	@Test
	public void preferOrderedTest() {
		TestHelper.createTester(PreferOrderedTest.class)//
		.withFailurePersistence(true).revisitKnownFailures(true).withDebug(true)//
		.withMaxCaseDuration(Duration.ofSeconds(1)).withRandomCases(50)//
		// .withConcurrency(p -> p - 2)//
		.withPlacemarks("op")//
		.execute()//
		.throwErrorIfFailed();
	}

	// @Test
	// public void disorderedTest() {
	// TestHelper.createTester(DisorderedTest.class)//
	// .withFailurePersistence(true).revisitKnownFailures(true).withDebug(true)//
	// .withMaxCaseDuration(Duration.ofSeconds(1)).withRandomCases(1000)//
	// .withConcurrency(p -> p - 2)//
	// .withPlacemarks("op")//
	// .execute()//
	// .throwErrorIfFailed();
	// }
	//
	// @Test
	// public void sortedTest() {
	// TestHelper.createTester(SortedTest.class)//
	// .withFailurePersistence(true).revisitKnownFailures(true).withDebug(true)//
	// .withMaxCaseDuration(Duration.ofSeconds(1)).withRandomCases(2000)//
	// .withConcurrency(p -> p - 2)//
	// .withPlacemarks("op")//
	// .execute()//
	// .throwErrorIfFailed();
	// }

	/** Abstract synchronization test */
	public static abstract class SyncTest implements Testable {
		static final int MAX_VALUE = 100;
		private final ObservableCollection<Integer> theLeft = createCollection(true);
		private final ObservableCollection<Integer> theRight = createCollection(false);

		/** @return The left collection being synchronized */
		public ObservableCollection<Integer> getLeft() {
			return theLeft;
		}

		/** @return The right collection being synchronized */
		public ObservableCollection<Integer> getRight() {
			return theRight;
		}

		@Override
		public void accept(TestHelper helper) {
			int size = helper.getInt(0, 15);
			for (int i = 0; i < size; i++)
				theLeft.add(helper.getInt(0, MAX_VALUE));

			size = helper.getInt(0, 15);
			for (int i = 0; i < size; i++)
				theRight.add(helper.getInt(0, MAX_VALUE));

			if (helper.isReproducing())
				System.out.println("At start,\n" + theLeft + "\n" + theRight);
			helper.placemark("op");
			ObservableCollectionSynchronization<Integer> sync = configureSynchronization(
				ObservableCollectionSynchronization.synchronize(theLeft, theRight))//
				.synchronize();
			checkSync();
			try {
				int operations = 500;
				for (int op = 0; op < operations; op++) {
					boolean leftOp = helper.getBoolean();
					ObservableCollection<Integer> opColl = leftOp ? theLeft : theRight;
					int preSize = opColl.size();
					int opIdx = op;
					TestHelper.RandomAction action = helper.createAction()//
						.or(.4, () -> { // Add
							int value = helper.getInt(0, MAX_VALUE);
							if (opColl.isContentControlled()) {
								if (helper.isReproducing())
									System.out.println("[" + opIdx + "]" + (leftOp ? "left" : "right") + " add " + value);
								opColl.add(value);
							} else {
								int index = helper.getInt(0, opColl.size() + 1);
								if (helper.isReproducing())
									System.out.println("[" + opIdx + "]" + (leftOp ? "left" : "right") + " add " + value + "@" + index);
								opColl.add(index, value);
								Assert.assertEquals(preSize + 1, opColl.size());
							}
						})//
						.or(.3, () -> { // Remove
							if (opColl.isEmpty())
								return;
							int index = helper.getInt(0, opColl.size());
							if (helper.isReproducing())
								System.out.println(
									"[" + opIdx + "]" + (leftOp ? "left" : "right") + " remove " + opColl.get(index) + "@" + index);
							opColl.remove(index);
							Assert.assertEquals(preSize - 1, opColl.size());
						});
					if (useSet()) {
						action.or(.2, () -> { // Set
							if (opColl.isEmpty())
								return;
							int index = helper.getInt(0, opColl.size());
							int value = helper.getInt(0, MAX_VALUE);
							if (helper.isReproducing())
								System.out.println("[" + opIdx + "]" + (leftOp ? "left" : "right") + " set " + opColl.get(index) + "->"
									+ value + "@" + index);
							opColl.set(index, value);
							Assert.assertEquals(preSize, opColl.size());
						});
					}
					action.or(.05, () -> { // Update
						if (opColl.isEmpty())
							return;
						int index = helper.getInt(0, opColl.size());
						if (helper.isReproducing())
							System.out.println(
								"[" + opIdx + "]" + (leftOp ? "left" : "right") + " update " + opColl.get(index) + "@" + index);
						opColl.set(index, opColl.get(index));
						Assert.assertEquals(preSize, opColl.size());
					});
					action.execute("op");
					if (helper.isReproducing())
						System.out.println(theLeft + "\n" + theRight);
					checkSync();
				}
			} finally {
				sync.unsubscribe();
			}
		}

		/**
		 * Checks the synchronization
		 *
		 * @throws AssertionError If the two collections are not synchronized
		 */
		protected void checkSync() throws AssertionError {
			QommonsTestUtils.assertThat(theRight, QommonsTestUtils.collectionsEqual(theLeft, isOrdered()));
		}

		/**
		 * @param left Whether to create the left or the right collection
		 *
		 * @return The new collection
		 */
		protected abstract ObservableCollection<Integer> createCollection(boolean left);

		/**
		 * @param sync The synchronization configuration
		 * @return The configured synchronization
		 */
		protected abstract Builder<Integer> configureSynchronization(Builder<Integer> sync);

		/** @return Whether this test should utilize {@link ObservableCollection#set(int, Object)} */
		protected abstract boolean useSet();

		/** @return Whether order should be enforced between the two collections */
		protected abstract boolean isOrdered();
	}

	/** Testable for {@link CollectionSynchronizationTest#simpleTest()} */
	public static class SimpleTest extends SyncTest {
		@Override
		protected ObservableCollection<Integer> createCollection(boolean left) {
			return ObservableCollection.<Integer> build().build();
		}

		@Override
		protected Builder<Integer> configureSynchronization(Builder<Integer> sync) {
			return sync.strictOrder();
		}

		@Override
		protected boolean useSet() {
			return true;
		}

		@Override
		protected boolean isOrdered() {
			return true;
		}
	}

	/** Testable for {@link CollectionSynchronizationTest#preferOrderedTest()} */
	public static class PreferOrderedTest extends SimpleTest {
		@Override
		protected Builder<Integer> configureSynchronization(Builder<Integer> sync) {
			return sync.preferOrdered();
		}

		@Override
		protected boolean isOrdered() {
			return false;
		}
	}
	//
	// public static class DisorderedTest extends SimpleTest {
	// @Override
	// protected Builder<Integer> configureSynchronization(Builder<Integer> sync) {
	// return sync.unordered();
	// }
	// }
	//
	// public static class SortedTest extends SyncTest {
	// @Override
	// protected ObservableCollection<Integer> createCollection(boolean left) {
	// if (left)
	// return ObservableCollection.build(int.class).build();
	// else
	// return ObservableSortedSet.build(int.class, Integer::compare).build();
	// }
	//
	// @Override
	// protected Builder<Integer> configureSynchronization(Builder<Integer> sync) {
	// return sync.preferOrdered();
	// }
	//
	// @Override
	// protected boolean useSet() {
	// return false;
	// }
	//
	// @Override
	// protected boolean isOrdered() {
	// return false;
	// }
	//
	// @Override
	// protected void checkSync() {
	// Assert.assertTrue("Some left missing in right", getLeft().containsAll(getRight()));
	// Assert.assertTrue("Some right missing in left", getRight().containsAll(getLeft()));
	// }
	// }
}
