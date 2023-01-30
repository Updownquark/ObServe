package org.observe.remote;

/**
 * A byte address is an address of an element in a sequence. ByteAddresses are comparable and new addresses can be added at any point within
 * any sequence of addresses--there is no minimum or maximum address, and no matter how close 2 addresses are, a new one can always be added
 * between them.
 */
public final class ByteAddress extends ByteArray {
	public ByteAddress(byte[] bytes) {
		super(bytes);
	}

	/**
	 * Produces a byte address between two addresses
	 *
	 * @param after The byte address to be less than the produced address (or null if there is no minimum)
	 * @param before The byte address to be greater than the produced address (or null if there is no maximum)
	 * @return A byte address that is greater than <code>after</code> (if not null) and less than <code>before</code> (if not null)
	 */
	public static ByteAddress between(ByteAddress after, ByteAddress before) {
		if (after == null && before == null)
			return new ByteAddress(new byte[1]);
		else if (after == null)
			return before(before, 0);
		else if (before == null)
			return after(after, 0);
		else if (after.size() == 0)
			throw new IllegalStateException("Cannot have zero-size byte addresses in collections");
		else if (before.size() == 0)
			throw new IllegalStateException("Cannot have zero-size byte addresses in collections");

		for (int i = 0;; i++) {
			if (i < after.size()) {
				if (i < before.size()) {
					if (after.get(i) == before.get(i)) {// continue;
					} else if (after.get(i) > before.get(i))
						throw new IllegalArgumentException(after + " > " + before);
					else if (after.get(i) == before.get(i) - 1)
						return between(after, before, i + 1);
					else {
						byte[] address = new byte[i + 1];
						after.copy(address, 0, i);
						address[i] = (byte) ((after.get(i) + before.get(i)) / 2);
						return new ByteAddress(address);
					}
				} else
					return after(after, i);
			} else if (i < before.size())
				return before(before, i);
			else
				throw new IllegalArgumentException("Addresses are equal: " + after);
		}
	}

	private static ByteAddress before(ByteAddress before, int byteIndex) {
		for (int i = byteIndex; i < before.size(); i++) {
			if (before.get(i) != Byte.MIN_VALUE) {
				byte[] address = new byte[i + 1];
				before.copy(address, 0, i);
				address[i] = (byte) (before.get(i) - 1);
				return new ByteAddress(address);
			}
		}
		byte[] address = new byte[before.size() + 1];
		before.copy(address, 0);
		address[before.size()] = -64;
		return new ByteAddress(address);
	}

	private static ByteAddress after(ByteAddress after, int byteIndex) {
		for (int i = byteIndex; i < after.size(); i++) {
			if (after.get(i) != Byte.MAX_VALUE) {
				byte[] address = new byte[i + 1];
				after.copy(address, 0, i);
				address[i] = (byte) (after.get(i) + 1);
				return new ByteAddress(address);
			}
		}
		byte[] address = new byte[after.size() + 1];
		after.copy(address, 0);
		address[after.size()] = 64;
		return new ByteAddress(address);
	}

	private static ByteAddress between(ByteAddress after, ByteAddress before, int byteIndex) {
		for (int i = byteIndex;; i++) {
			if (i == after.size()) {
				byte[] address = new byte[i + 1];
				after.copy(address, 0, i);
				address[i] = 64;
				return new ByteAddress(address);
			} else if (i == before.size()) {
				byte[] address = new byte[i + 1];
				before.copy(address, 0, i);
				address[i] = -64;
				return new ByteAddress(address);
			} else if (after.get(i) != Byte.MAX_VALUE) {
				byte[] address = new byte[i + 1];
				after.copy(address, 0, i);
				address[i] = (byte) ((after.get(i) + Byte.MAX_VALUE + 1) / 2);
				return new ByteAddress(address);
			} else if (before.get(i) != Byte.MIN_VALUE) {
				byte[] address = new byte[i + 1];
				before.copy(address, 0, i);
				address[i] = (byte) ((before.get(i) + Byte.MIN_VALUE - 1) / 2);
				return new ByteAddress(address);
			}
		}
	}
}