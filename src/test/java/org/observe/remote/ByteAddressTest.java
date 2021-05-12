package org.observe.remote;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;
import org.qommons.TestHelper;
import org.qommons.TestHelper.Testable;

/** Tests {@link ByteAddress} */
public class ByteAddressTest implements Testable {
	/**
	 * This random test ensures that the ByteAddress class has its advertised qualities:
	 * <ul>
	 * <li>A new ByteAddress can be produced at the beginning or at the end of any sequence of addresses</li>
	 * <li>A new ByteAddress can be produced that is between any other two ByteAddresses</li>
	 * <li>A ByteAddress produced for any point in any address sequence is greater than addresses before that point and less than addresses
	 * after that point</li>
	 * </ul>
	 */
	@Test
	public void testByteAddress() {
		TestHelper.createTester(ByteAddressTest.class).withDebug(true).withFailurePersistence(true).withRandomCases(2_000)
		.withMaxCaseDuration(Duration.ofSeconds(2))//
		.withPlacemarks("address").execute();
	}

	@Override
	public void accept(TestHelper helper) {
		ByteAddress.between(null, null); // Test the simplest case, though we don't use it for the test body

		int addressCount = helper.getInt(20, 100);
		List<ByteAddress> addresses = new ArrayList<>(addressCount);
		addresses.add(new ByteAddress(helper.getBytes(helper.getInt(1, 10))));// Initialize with a random address

		for (int i = 1; i < addressCount; i++) {
			helper.placemark("address");
			int index = helper.getInt(0, i + 1);
			ByteAddress after = index == 0 ? null : addresses.get(index - 1);
			ByteAddress before = index == i ? null : addresses.get(index);
			ByteAddress toAdd = ByteAddress.between(after, before);
			for (int j = 0; j < index; j++) {
				helper.placemark();
				Assert.assertTrue(addresses.get(j).compareTo(toAdd) < 0);
			}
			for (int j = index; j < i; j++) {
				helper.placemark();
				Assert.assertTrue(addresses.get(j).compareTo(toAdd) > 0);
			}

			addresses.add(index, toAdd);
		}
	}
}
