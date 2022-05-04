package org.observe.remote;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;

import org.qommons.StringUtils;

/**
 * A byte address is an address of an element in a sequence. ByteAddresses are comparable and new addresses can be added at any point within
 * any sequence of addresses--there is no minimum or maximum address, and no matter how close 2 addresses are, a new one can always be added
 * between them.
 */
public final class ByteAddress implements Comparable<ByteAddress> {
	private byte[] bytes;

	/** @param bytes The address bytes */
	public ByteAddress(byte[] bytes) {
		if (bytes.length == 0)
			throw new IllegalArgumentException("Empty address");
		this.bytes = bytes;
	}

	/** @return The number of bytes in this address */
	public int size() {
		return bytes.length;
	}

	/**
	 * @param index The index of the byte to get
	 * @return The byte at the given index of this address
	 */
	public byte get(int index) {
		return bytes[index];
	}

	/**
	 * Copies this address into another byte array
	 *
	 * @param into The byte array into which to copy this address
	 */
	public void copy(byte[] into) {
		System.arraycopy(bytes, 0, into, 0, bytes.length);
	}

	/**
	 * @param out The output stream to write to
	 * @throws IOException If the stream throws an exception
	 */
	public void write(OutputStream out) throws IOException {
		out.write(bytes);
	}

	@Override
	public int compareTo(ByteAddress other) {
		int i;
		for (i = 0; i < bytes.length && i < other.bytes.length; i++) {
			int comp = Byte.compare(bytes[i], other.bytes[i]);
			if (comp != 0)
				return comp;
		}
		for (; i < bytes.length; i++) {
			if (bytes[i] != 0)
				return bytes[i];
		}
		for (; i < other.bytes.length; i++) {
			if (other.bytes[i] != 0)
				return -other.bytes[i];
		}
		return 0;
	}

	@Override
	public int hashCode() {
		int hash = 0;
		for (int i = 0; i < bytes.length / 4; i++) {
			int fourByteHash = bytes[i];
			fourByteHash = (fourByteHash << 8) | bytes[i + 1];
			fourByteHash = (fourByteHash << 8) | bytes[i + 2];
			fourByteHash = (fourByteHash << 8) | bytes[i + 3];
			hash ^= fourByteHash;
		}
		return hash;
	}

	@Override
	public boolean equals(Object obj) {
		return obj instanceof ByteAddress && Arrays.equals(bytes, ((ByteAddress) obj).bytes);
	}

	@Override
	public String toString() {
		return StringUtils.encodeHex().format(bytes);
	}

	/**
	 * Parses a hex-encoded address (e.g. produced by {@link #toString()})
	 *
	 * @param address The hex-encoded address to parse
	 * @return The parsed byte address
	 */
	public static ByteAddress of(String address) {
		return new ByteAddress(StringUtils.encodeHex().parse(address));
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

		for (int i = 0;; i++) {
			if (i < after.bytes.length) {
				if (i < before.bytes.length) {
					if (after.bytes[i] == before.bytes[i]) {// continue;
					} else if (after.bytes[i] > before.bytes[i])
						throw new IllegalArgumentException(after + " > " + before);
					else if (after.bytes[i] == before.bytes[i] - 1)
						return between(after, before, i + 1);
					else {
						byte[] address = new byte[i + 1];
						System.arraycopy(after.bytes, 0, address, 0, i);
						address[i] = (byte) ((after.bytes[i] + before.bytes[i]) / 2);
						return new ByteAddress(address);
					}
				} else
					return after(after, i);
			} else if (i < before.bytes.length)
				return before(before, i);
			else
				throw new IllegalArgumentException("Addresses are equal: " + after);
		}
	}

	private static ByteAddress before(ByteAddress before, int byteIndex) {
		for (int i = byteIndex; i < before.bytes.length; i++) {
			if (before.bytes[i] != Byte.MIN_VALUE) {
				byte[] address = new byte[i + 1];
				System.arraycopy(before.bytes, 0, address, 0, i);
				address[i] = (byte) (before.bytes[i] - 1);
				return new ByteAddress(address);
			}
		}
		byte[] address = new byte[before.bytes.length + 1];
		System.arraycopy(before.bytes, 0, address, 0, before.bytes.length);
		address[before.bytes.length] = -64;
		return new ByteAddress(address);
	}

	private static ByteAddress after(ByteAddress after, int byteIndex) {
		for (int i = byteIndex; i < after.bytes.length; i++) {
			if (after.bytes[i] != Byte.MAX_VALUE) {
				byte[] address = new byte[i + 1];
				System.arraycopy(after.bytes, 0, address, 0, i);
				address[i] = (byte) (after.bytes[i] + 1);
				return new ByteAddress(address);
			}
		}
		byte[] address = new byte[after.bytes.length + 1];
		System.arraycopy(after.bytes, 0, address, 0, after.bytes.length);
		address[after.bytes.length] = 64;
		return new ByteAddress(address);
	}

	private static ByteAddress between(ByteAddress after, ByteAddress before, int byteIndex) {
		for (int i = byteIndex;; i++) {
			if (i == after.bytes.length) {
				byte[] address = new byte[i + 1];
				System.arraycopy(after.bytes, 0, address, 0, i);
				address[i] = 64;
				return new ByteAddress(address);
			} else if (i == before.bytes.length) {
				byte[] address = new byte[i + 1];
				System.arraycopy(before.bytes, 0, address, 0, i);
				address[i] = -64;
				return new ByteAddress(address);
			} else if (after.bytes[i] != Byte.MAX_VALUE) {
				byte[] address = new byte[i + 1];
				System.arraycopy(after.bytes, 0, address, 0, i);
				address[i] = (byte) ((after.bytes[i] + Byte.MAX_VALUE + 1) / 2);
				return new ByteAddress(address);
			} else if (before.bytes[i] != Byte.MIN_VALUE) {
				byte[] address = new byte[i + 1];
				System.arraycopy(before.bytes, 0, address, 0, i);
				address[i] = (byte) ((before.bytes[i] + Byte.MIN_VALUE - 1) / 2);
				return new ByteAddress(address);
			}
		}
	}
}