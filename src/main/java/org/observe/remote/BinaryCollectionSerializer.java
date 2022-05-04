package org.observe.remote;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.function.Function;

import org.observe.collect.CollectionChangeType;

public class BinaryCollectionSerializer<E> implements CollectionConnectionSerializer<E, byte[], byte[]> {
	private final Function<? super E, byte[]> theValueSerializer;
	private final Function<byte[], ? extends E> theValueDeserializer;

	public BinaryCollectionSerializer(Function<? super E, byte[]> valueSerializer, Function<byte[], ? extends E> valueDeserializer) {
		theValueSerializer = valueSerializer;
		theValueDeserializer = valueDeserializer;
	}

	@Override
	public byte[] serializeValue(E value) {
		return theValueSerializer.apply(value);
	}

	@Override
	public E deserializeValue(byte[] serialized) {
		return theValueDeserializer.apply(serialized);
	}

	@Override
	public byte[] serializeChange(SerializedCollectionChange<? extends E> change) {
		byte[] value = null, oldValue = null;
		int addressSize = 0;
		switch (change.type) {
		case add:
			addressSize = 1;
			value = theValueSerializer.apply(change.newValue);
			oldValue = null;
			break;
		case remove:
			addressSize = 2;
			value = theValueSerializer.apply(change.oldValue);
			oldValue = null;
			break;
		case set:
			if (change.oldValue == change.newValue) {
				addressSize = 4;
				value = oldValue = null;
			} else {
				addressSize = 3;
				value = theValueSerializer.apply(change.oldValue);
				oldValue = theValueSerializer.apply(change.oldValue);
			}
			break;
		}
		addressSize <<= 2;
		if (change.move)
			addressSize |= 1;
		addressSize <<= 1;
		if (change.transactionEnd)
			addressSize |= 1;
		addressSize = (addressSize << 24) | change.elementId.size();
		int size = 8 // Event ID
			+ 3 // address length -- support up to 65,536 byte address size
			+ 1; // change type, move, transaction end
		if (oldValue != null)
			size += 4 + oldValue.length;
		if (value != null)
			size += 4 + value.length;
		ByteArrayOutputStream bytes = new ByteArrayOutputStream(size);
		DataOutputStream out = new DataOutputStream(bytes);
		try {
			out.writeLong(change.eventId);
			out.writeInt(addressSize);
			change.elementId.write(out);
			out.writeInt(oldValue == null ? 0 : oldValue.length);
			out.writeInt(value == null ? 0 : value.length);
			if (oldValue != null)
				out.write(oldValue);
			if (value != null)
				out.write(value);
		} catch (IOException e) {
			throw new IllegalStateException("Should not happen", e);
		}
		return bytes.toByteArray();
	}

	@Override
	public SerializedCollectionChange<E> deserializeChange(byte[] change) {
		DataInputStream in = new DataInputStream(new ByteArrayInputStream(change));
		try {
			long eventId = in.readLong();
			int addressSize = in.readInt();
			byte[] address = new byte[addressSize & 0xffffff];
			in.readFully(address);
			addressSize >>= 24;
			boolean transactionEnd = (addressSize & 1) == 1;
			boolean move = (addressSize & 2) == 2;
			addressSize >>= 26;
			CollectionChangeType type;
			byte[] valueBytes = null;
			E oldValue = null, newValue = null;
			switch (addressSize) {
			case 1:
				type = CollectionChangeType.add;
				int valueSize = in.readInt();
				valueBytes = new byte[valueSize];
				in.readFully(valueBytes);
				newValue = theValueDeserializer.apply(valueBytes);
				break;
			case 2:
				type = CollectionChangeType.remove;
				valueSize = in.readInt();
				valueBytes = new byte[valueSize];
				in.readFully(valueBytes);
				oldValue = newValue = theValueDeserializer.apply(valueBytes);
				break;
			case 3:
				type = CollectionChangeType.set;
				valueSize = in.readInt();
				valueBytes = new byte[valueSize];
				in.readFully(valueBytes);
				oldValue = theValueDeserializer.apply(valueBytes);
				valueSize = in.readInt();
				if (valueSize != valueBytes.length)
					valueBytes = new byte[valueSize];
				in.readFully(valueBytes);
				newValue = theValueDeserializer.apply(valueBytes);
				break;
			case 4:
				type = CollectionChangeType.set;
				break;
			default:
				throw new IllegalStateException("bad event type: " + addressSize);
			}
			return new SerializedCollectionChange<>(eventId, new ByteAddress(address), type, move, oldValue, newValue, transactionEnd);
		} catch (IOException e) {
			throw new IllegalStateException("Should not happen", e);
		}
	}
}
