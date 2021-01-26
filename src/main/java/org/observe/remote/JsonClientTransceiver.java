package org.observe.remote;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.observe.collect.CollectionChangeType;
import org.qommons.Transaction;
import org.qommons.ex.CheckedExceptionWrapper;
import org.qommons.json.JsonSerialReader;
import org.qommons.json.JsonSerialReader.JsonParseItem;
import org.qommons.json.JsonSerialReader.StructState;
import org.qommons.json.JsonStreamWriter;
import org.qommons.json.SAJParser.ParseException;

public class JsonClientTransceiver<P>
implements CollectionClientTransceiver<P, JsonClientTransceiver.JsonOp<P>> {
	public static class JsonOp<P> {
		final CollectionChangeType type;
		final ByteList address1;
		final ByteList address2;
		final boolean first;
		final P value;

		public JsonOp(CollectionChangeType type, ByteList address1, ByteList address2, boolean first, P value) {
			this.type = type;
			this.address1 = address1;
			this.address2 = address2;
			this.first = first;
			this.value = value;
		}
	}

	public interface JsonSerializer<P> {
		void write(P value, JsonStreamWriter json) throws IOException;

		P parse(JsonSerialReader json) throws IOException, ParseException;
	}

	interface JsonCommand {
		void configure(JsonStreamWriter json) throws IOException;
	}

	interface JsonResult<T, P> {
		T parse(String type, List<SerializedCollectionChange<P>> changes, JsonSerialReader json) throws IOException, ParseException;
	}

	private final TextTransfer theTransfer;
	private final JsonSerializer<P> theSerializer;
	private Boolean isContentControlled;
	private long theLastChange;

	public JsonClientTransceiver(TextTransfer transfer, JsonSerializer<P> serializer) {
		theTransfer = transfer;
		theSerializer = serializer;
		theLastChange = -1;
	}

	<T> T send(String commandName, JsonCommand command, JsonResult<T, P> result) throws IOException {
		TextTransfer.TextTransaction t = theTransfer.createTransaction();
		try (Writer writer = t.write()) {
			JsonStreamWriter json = new JsonStreamWriter(t.write());
			json.startObject().startProperty("command").writeString(commandName);
			json.startProperty("lastChange").writeNumber(theLastChange);
			if (command != null)
				command.configure(json);
			json.endObject();
		}
		try (Reader response = t.send()) {
			JsonSerialReader reader = new JsonSerialReader(response);
			try {
				StructState root = reader.startObject();
				String firstProp = reader.getNextProperty();
				if (!"responseType".equals(firstProp))
					throw new IllegalStateException("Unrecognized response data: " + firstProp);
				String responseType = reader.parseString();
				String secondProp = reader.getNextProperty();
				if (!"changes".equals(secondProp))
					throw new IllegalStateException("Unrecognized response data: " + secondProp);
				StructState changesState = reader.startArray();
				JsonParseItem nextChange = reader.getNextItem(true, false);
				List<SerializedCollectionChange<P>> changes = null;
				while (nextChange != null) {
					switch (nextChange.getType()) {
					case OBJECT:
						if (changes == null)
							changes = new ArrayList<>();
						changes.add(parseChange(reader));
						break;
					case ARRAY:
						break;
					default:
						throw new IllegalStateException("Unrecognized value in changes list: " + nextChange.getType());
					}
				}
				if (changes == null)
					changes = Collections.emptyList();
				reader.endArray(changesState);
				T ret = result.parse(responseType, changes, reader);
				reader.endObject(root);
				return ret;
			} catch (ParseException e) {
				throw new IllegalStateException("Unrecognized " + commandName + " return data", e);
			}
		}
	}

	SerializedCollectionChange<P> parseChange(JsonSerialReader reader) throws IOException, ParseException {
		String prop = reader.getNextProperty();
		if (!"id".equals(prop))
			throw new IllegalStateException("Unrecognized change format: " + prop);
		long eventId = reader.parseLong();
		prop = reader.getNextProperty();
		if (!"type".equals(prop))
			throw new IllegalStateException("Unrecognized change format: " + prop);
		String typeName = reader.parseString();
		prop = reader.getNextProperty();
		if (!"address".equals(prop))
			throw new IllegalStateException("Unrecognized change format: " + prop);
		ByteList address = ByteList.of(reader.parseString());
		CollectionChangeType type;
		P oldValue, newValue;
		switch (typeName) {
		case "add":
			type = CollectionChangeType.add;
			oldValue = null;
			prop = reader.getNextProperty();
			if (!"value".equals(prop))
				throw new IllegalStateException("Unrecognized change format: " + prop);
			newValue = theSerializer.parse(reader);
			break;
		case "remove":
			type = CollectionChangeType.remove;
			oldValue = newValue = null;
			break;
		case "set":
			type = CollectionChangeType.set;
			prop = reader.getNextProperty();
			if (!"old".equals(prop))
				throw new IllegalStateException("Unrecognized change format: " + prop);
			oldValue = theSerializer.parse(reader);
			prop = reader.getNextProperty();
			if (!"new".equals(prop))
				throw new IllegalStateException("Unrecognized change format: " + prop);
			newValue = theSerializer.parse(reader);
			break;
		case "update":
			type = CollectionChangeType.set;
			prop = reader.getNextProperty();
			if (!"value".equals(prop))
				throw new IllegalStateException("Unrecognized change format: " + prop);
			oldValue = newValue = theSerializer.parse(reader);
			break;
		default:
			throw new IllegalStateException("Unrecognized change format: " + typeName);
		}
		return new SerializedCollectionChange<>(eventId, address, type, oldValue, newValue);
	}

	@Override
	public long getLastChange() {
		return theLastChange;
	}

	@Override
	public void setLastChange(long lastChange) {
		theLastChange = lastChange;
	}

	@Override
	public LockResult<P> lock(boolean write) throws IOException {
		return lock("lock", write);
	}

	@Override
	public LockResult<P> tryLock(boolean write) throws IOException {
		return lock("tryLock", write);
	}

	LockResult<P> lock(String command, boolean write) throws IOException {
		List<SerializedCollectionChange<P>>[] changes = new List[1];
		String lockId = send(command, json -> json.startProperty("write").writeBoolean(write), //
			(type, changes2, json) -> {
				changes[0] = changes2;
				switch (type) {
				case "error":
					throw new IllegalStateException(json.parseString());
				case "fail":
					return null;
				case "success":
					String prop = json.getNextProperty();
					if (!"lockId".equals(prop))
						throw new IllegalStateException("Unrecognized lock return: " + prop);
					return json.parseString();
				default:
					throw new IllegalStateException("Unrecognized lock return: " + type);
				}
			});
		if (lockId == null)
			return null;
		return new LockResult<>(changes[0], new Transaction() {
			private volatile boolean isClosed;

			@Override
			public void close() {
				if (isClosed)
					return;
				isClosed = true;
				int tries = 0;
				while (tries < 5) {
					tries++;
					try {
						send("unlock", json -> json.startProperty("lockId").writeString(lockId), //
							(type, changes2, json) -> {
								switch (type) {
								case "error":
									throw new IllegalStateException(json.parseString());
								case "success":
									return null;
								default:
									throw new IllegalStateException(type);
								}
							});
						return;
					} catch (IOException e) {
						if (tries == 5)
							throw new IllegalStateException("Unable to release remote lock", e);
					}
				}
			}
		});
	}

	@Override
	public boolean isContentControlled() throws IOException {
		if (isContentControlled == null) {
			isContentControlled = send("queryContentControlled", null, (type, changes, json) -> {
				if (!type.equals("success"))
					return null;
				String prop = json.getNextProperty();
				if (!"controlled".equals(prop))
					throw new IllegalStateException("Unrecognized queryContentControlled result: " + prop);
				return json.parseBoolean();
			});
			if (isContentControlled == null)
				return true; // Be safe
		}
		return isContentControlled;
	}

	@Override
	public CollectionPollResult<P> poll() throws IOException {
		return send("poll", null, (type, changes, json) -> new CollectionPollResult<>(changes));
	}

	@Override
	public JsonOp<P> add(P value, ByteList after, ByteList before, boolean first) {
		return new JsonOp<>(CollectionChangeType.add, after, before, first, value);
	}

	@Override
	public JsonOp<P> remove(ByteList element) {
		return new JsonOp<>(CollectionChangeType.remove, element, null, false, null);
	}

	@Override
	public JsonOp<P> set(ByteList element, P value) {
		return new JsonOp<>(CollectionChangeType.set, element, null, false, value);
	}

	@Override
	public JsonOp<P> update(ByteList element) {
		return new JsonOp<>(CollectionChangeType.set, element, element, false, null);
	}

	@Override
	public String queryCapability(List<JsonOp<P>> queries) throws IOException, ConcurrentRemoteModException {
		return send("queryCapability", json->{
			json.startProperty("ops").startArray();
			for(JsonOp<P> op : queries){
				json.startObject();
				json.startProperty("type");
				switch(op.type){
				case add:
					json.writeString("add");
					if(op.address1!=null)
						json.startProperty("after").writeString(op.address1.toString());
					if (op.address2 != null)
						json.startProperty("before").writeString(op.address2.toString());
					json.startProperty("value");
					theSerializer.write(op.value, json);
					break;
				case remove:
					json.writeString("remove");
					json.startProperty("address").writeString(op.address1.toString());
					break;
				case set:
					json.writeString(op.address2 == null ? "set" : "update");
					if (op.address2 == null) {
						json.startProperty("value");
						theSerializer.write(op.value, json);
					}
					break;
				}
				json.endObject();
			}
			json.endArray();
		}, (type, changes, json)->{
			if ("success".equals(type)) {
				String prop=json.getNextProperty();
				if(!"result".equals(prop))
					throw new IllegalStateException("Unrecognized queryCapability result: " + prop);
				JsonParseItem next = json.getNextItem(true, false);
				switch (next.getType()) {
				case PRIMITIVE:
					Object result = ((JsonSerialReader.PrimitiveItem) next).getValue();
					if (result == null || result instanceof String)
						return (String) result;
					throw new IllegalStateException("Unrecognized queryCapability result: " + result);
				default:
					throw new IllegalStateException("Unrecognized queryCapability result: " + next);
				}
			} else
				throw new IllegalStateException("Unrecognized queryCapability result: " + type);
		});
	}

	@Override
	public OperationResultSet<P> applyOperations(List<JsonOp<P>> operations) throws IOException, ConcurrentRemoteModException {
		try {
			return send("apply", json -> {
				json.startProperty("ops").startArray();
				for (JsonOp<P> op : operations) {
					json.startObject();
					json.startProperty("type");
					switch (op.type) {
					case add:
						json.writeString("add");
						if (op.address1 != null)
							json.startProperty("after").writeString(op.address1.toString());
						if (op.address2 != null)
							json.startProperty("before").writeString(op.address2.toString());
						json.startProperty("first").writeBoolean(op.first);
						json.startProperty("value");
						theSerializer.write(op.value, json);
						break;
					case remove:
						json.writeString("remove");
						json.startProperty("address").writeString(op.address1.toString());
						break;
					case set:
						json.writeString(op.address2 == null ? "set" : "update");
						if (op.address2 == null) {
							json.startProperty("value");
							theSerializer.write(op.value, json);
						}
						break;
					}
					json.endObject();
				}
				json.endArray();
			}, (type, changes, json) -> {
				switch (type) {
				case "concurrentMod":
					throw new CheckedExceptionWrapper(new ConcurrentRemoteModException(changes));
				case "error":
					json.getNextProperty();
					return new OperationResultSet<>(changes, null, json.parseString());
				case "success":
					json.getNextProperty();
					return new OperationResultSet<>(changes, ByteList.of(json.parseString()), null);
				default:
					throw new IllegalStateException("Unrecognized applyOperations result: " + type);
				}
			});
		} catch (CheckedExceptionWrapper e) {
			throw (ConcurrentRemoteModException) e.getCause();
		}
	}
}
