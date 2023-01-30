package org.observe.remote;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;

import org.qommons.StringUtils;

/** An immutable wrapper around a byte array */
public class ByteArray implements Comparable<ByteArray> {
	private byte[] bytes;

	/** @param bytes The address bytes */
	public ByteArray(byte[] bytes) {
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

	/** @return A copy of this byte array */
	public byte[] copy() {
		return bytes.clone();
	}

	/**
	 * Copies this address into another byte array
	 *
	 * @param into The byte array into which to copy this address
	 * @param offset The offset in the target array to start copying at
	 */
	public void copy(byte[] into, int offset) {
		copy(into, offset, bytes.length);
	}

	/**
	 * Copies this address into another byte array
	 *
	 * @param into The byte array into which to copy this address
	 * @param offset The offset in the target array to start copying at
	 * @param length The number of bytes in this array to copy
	 */
	public void copy(byte[] into, int offset, int length) {
		System.arraycopy(bytes, 0, into, offset, length);
	}

	/** @param into The byte buffer to append this array into */
	public void putInto(ByteBuffer into) {
		into.put(bytes);
	}

	/**
	 * @param out The output stream to write to
	 * @throws IOException If the stream throws an exception
	 */
	public void write(OutputStream out) throws IOException {
		out.write(bytes);
	}

	@Override
	public int compareTo(ByteArray other) {
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
		return obj instanceof ByteArray && Arrays.equals(bytes, ((ByteArray) obj).bytes);
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
	public static ByteArray of(String address) {
		return new ByteArray(StringUtils.encodeHex().parse(address));
	}

}
