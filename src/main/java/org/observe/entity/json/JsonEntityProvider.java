package org.observe.entity.json;

import java.io.IOException;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.LongConsumer;

import org.observe.Observable;
import org.observe.SimpleObservable;
import org.observe.entity.ConfigurableCreator;
import org.observe.entity.ConfigurableDeletion;
import org.observe.entity.ConfigurableOperation;
import org.observe.entity.ConfigurableQuery;
import org.observe.entity.ConfigurableUpdate;
import org.observe.entity.EntityChainAccess;
import org.observe.entity.EntityChange;
import org.observe.entity.EntityChange.FieldChange;
import org.observe.entity.EntityCondition;
import org.observe.entity.EntityCreator;
import org.observe.entity.EntityDeletion;
import org.observe.entity.EntityFieldSetOperation;
import org.observe.entity.EntityIdentity;
import org.observe.entity.EntityLoadRequest;
import org.observe.entity.EntityLoadRequest.Fulfillment;
import org.observe.entity.EntityOperationException;
import org.observe.entity.EntityQuery;
import org.observe.entity.EntitySetOperation;
import org.observe.entity.EntityUpdate;
import org.observe.entity.EntityValueAccess;
import org.observe.entity.ObservableEntityDataSet;
import org.observe.entity.ObservableEntityFieldType;
import org.observe.entity.ObservableEntityProvider;
import org.observe.entity.ObservableEntityType;
import org.observe.entity.PreparedCreator;
import org.observe.entity.PreparedDeletion;
import org.observe.entity.PreparedQuery;
import org.observe.entity.PreparedUpdate;
import org.qommons.QommonsUtils;
import org.qommons.Transaction;
import org.qommons.collect.BetterList;
import org.qommons.collect.QuickSet.QuickMap;
import org.qommons.collect.StampedLockingStrategy;
import org.qommons.json.JsonSerialReader;
import org.qommons.json.JsonSerialReader.JsonParseItem;
import org.qommons.json.JsonSerialReader.StructState;
import org.qommons.json.JsonSerialWriter;
import org.qommons.json.JsonStreamWriter;
import org.qommons.json.SAJParser;
import org.qommons.tree.BetterTreeList;

import com.google.common.reflect.TypeToken;

public class JsonEntityProvider implements ObservableEntityProvider {
	private final StampedLockingStrategy theLocker;
	private final Writer theConnectionOutput;
	private final ConcurrentHashMap<Long, JsonAction> theRequests;
	private final AtomicLong theRequestIdGenerator;
	private final SimpleObservable<List<EntityChange<?>>> theChanges;

	private ObservableEntityDataSet theEntitySet;

	public JsonEntityProvider(StampedLockingStrategy locker, Writer connectionOutput) {
		theLocker = locker;
		theConnectionOutput = connectionOutput;
		theRequests = new ConcurrentHashMap<>();
		theRequestIdGenerator = new AtomicLong();
		theChanges = SimpleObservable.build().withLock(locker).build();
	}

	@Override
	public Transaction lock(boolean write, Object cause) {
		return theLocker.lock(write, cause);
	}

	@Override
	public Transaction tryLock(boolean write, Object cause) {
		return theLocker.tryLock(write, cause);
	}

	@Override
	public void install(ObservableEntityDataSet entitySet) throws IllegalStateException {
		theEntitySet = entitySet;
		int todo = todo;// TODO Compile (de-)serializers
	}

	@Override
	public Object prepare(ConfigurableOperation<?> operation) throws EntityOperationException {
		StringWriter sw = new StringWriter();
		long requestId = theRequestIdGenerator.getAndIncrement();
		JsonSerialWriter jsw = new JsonStreamWriter(sw);
		try {
			jsw.startObject();
			jsw.startProperty("id").writeNumber(requestId);
			jsw.startProperty("sync").writeBoolean(true);
			jsw.startProperty("action").writeString("prepare");
			jsw.startProperty("type").writeString(operation.getEntityType().getName());
			if (operation instanceof ConfigurableCreator) {
				jsw.startProperty("operation").writeString("create");
			} else if (operation instanceof ConfigurableQuery) {
				jsw.startProperty("operation").writeString("query");
			} else if (operation instanceof ConfigurableUpdate) {
				jsw.startProperty("operation").writeString("update");
			} else if (operation instanceof ConfigurableDeletion) {
				jsw.startProperty("operation").writeString("delete");
			} else
				throw new UnsupportedOperationException("Unrecognized operation type: " + operation.getClass().getName());
			if (operation instanceof EntitySetOperation) {
				jsw.startProperty("selection");
				serializeCondition(jsw, ((EntitySetOperation<?>) operation).getSelection());
			}
			if (operation instanceof EntityFieldSetOperation) {
				jsw.startProperty("fields").startObject();
				ConfigurableCreator<?> cc = (ConfigurableCreator<?>) operation;
				for (int f = 0; f < cc.getEntityType().getFields().keySize(); f++) {
					Object value = cc.getFieldValues().get(f);
					if (value != EntityUpdate.NOT_SET) {
						jsw.startProperty(cc.getEntityType().getFields().get(f).getName());
						serialize(jsw, value);
					}
				}
				jsw.endObject(); // End fields
			}
			if (operation instanceof ConfigurableQuery) {
				ConfigurableQuery<?> cq = (ConfigurableQuery<?>) operation;
				jsw.startProperty("load").startObject();
				for (int f = 0; f < cq.getFieldLoadTypes().keySize(); f++) {
					if (cq.getFieldLoadTypes().get(f) != null)
						jsw.startProperty(cq.getFieldLoadTypes().keySet().get(f)).writeString(cq.getFieldLoadTypes().get(f).name());
				}
			}
			jsw.endObject(); // End request
		} catch (IOException e) {
			// Should not happen--writing to a String
		}

		String[] prepared = new String[1];
		sendRequest(sw.toString(), requestId, jsr -> {
			String propName = jsr.getNextProperty();
			if (!propName.equals("prepared"))
				throw new EntityOperationException("Could not decode response--prepared property expected but found " + propName);
			prepared[0] = jsr.parseString();
		}, null, true);
		return prepared;
	}

	@Override
	public <E> SimpleEntity<E> create(EntityCreator<E> creator, Object prepared, Consumer<SimpleEntity<E>> identityFieldsOnAsyncComplete,
		Consumer<EntityOperationException> onError) throws EntityOperationException {
		StringWriter sw = new StringWriter();
		long requestId = theRequestIdGenerator.getAndIncrement();
		JsonSerialWriter jsw = new JsonStreamWriter(sw);
		try {
			jsw.startObject();
			jsw.startProperty("id").writeNumber(requestId);
			jsw.startProperty("sync").writeBoolean(identityFieldsOnAsyncComplete == null);
			jsw.startProperty("action").writeString("create");
			if (prepared != null) {
				jsw.startProperty("prepared");
				serializePrepared(jsw, prepared);
			}
			jsw.startProperty("type").writeString(creator.getEntityType().getName());
			if (creator instanceof ConfigurableCreator) {
				jsw.startProperty("fields").startObject();
				ConfigurableCreator<E> cc = (ConfigurableCreator<E>) creator;
				for (int f = 0; f < creator.getEntityType().getFields().keySize(); f++) {
					Object value = cc.getFieldValues().get(f);
					if (value != EntityUpdate.NOT_SET) {
						jsw.startProperty(creator.getEntityType().getFields().get(f).getName());
						serialize(jsw, value);
					}
				}
				jsw.endObject(); // End fields
			} else {
				jsw.startProperty("variables").startObject();
				PreparedCreator<E> pc = (PreparedCreator<E>) creator;
				for (int v = 0; v < pc.getVariableValues().keySize(); v++) {
					jsw.startProperty(pc.getVariableValues().keySet().get(v));
					serialize(jsw, pc.getVariableValues().get(v));
				}
				jsw.endObject(); // End variables
			}
			jsw.endObject(); // End request
		} catch (IOException e) {
			// Should not happen--writing to a String
		}
		SimpleEntity<E>[] entity = new SimpleEntity[1];
		sendRequest(sw.toString(), requestId, jsr -> {
			String propName = jsr.getNextProperty();
			if (!propName.equals("created"))
				throw new EntityOperationException("Could not decode response--created property expected but found " + propName);
			entity[0] = (SimpleEntity<E>) parseEntity(jsr);
		}, onError, identityFieldsOnAsyncComplete == null);
		return entity[0];
	}

	@Override
	public long count(EntityQuery<?> query, Object prepared, LongConsumer onAsyncComplete, Consumer<EntityOperationException> onError)
		throws EntityOperationException {
		StringWriter sw = new StringWriter();
		long requestId = theRequestIdGenerator.getAndIncrement();
		JsonSerialWriter jsw = new JsonStreamWriter(sw);
		try {
			jsw.startObject();
			jsw.startProperty("id").writeNumber(requestId);
			jsw.startProperty("sync").writeBoolean(false);
			jsw.startProperty("action").writeString("count");
			if (prepared != null) {
				jsw.startProperty("prepared");
				serializePrepared(jsw, prepared);
			}
			jsw.startProperty("type").writeString(query.getEntityType().getName());
			if(query instanceof ConfigurableQuery){
				ConfigurableQuery<?> cq=(ConfigurableQuery<?>) query;
				jsw.startProperty("selection");
				serializeCondition(jsw, cq.getSelection());
			} else{
				jsw.startProperty("variables").startObject();
				PreparedQuery<?> pq=(PreparedQuery<?>) query;
				for (int v = 0; v < pq.getVariableValues().keySize(); v++) {
					jsw.startProperty(pq.getVariableValues().keySet().get(v));
					serialize(jsw, pq.getVariableValues().get(v));
				}
				jsw.endObject(); // End variables
			}
			jsw.startProperty("load").startObject();
			for (int f = 0; f < query.getFieldLoadTypes().keySize(); f++) {
				if (query.getFieldLoadTypes().get(f) != null)
					jsw.startProperty(query.getFieldLoadTypes().keySet().get(f)).writeString(query.getFieldLoadTypes().get(f).name());
			}
			jsw.endObject(); // End loading
			jsw.endObject(); // End request
		} catch (IOException e) {
			// Should not happen--writing to a String
		}
		long[] count = new long[1];
		sendRequest(sw.toString(), requestId, jsr -> {
			String propName=jsr.getNextProperty();
			if(!propName.equals("count"))
				throw new EntityOperationException("Could not decode response--count property expected but found " + propName);
			count[0] = jsr.parseNumber().longValue();
			if (onAsyncComplete != null)
				onAsyncComplete.accept(count[0]);
		}, onError, onAsyncComplete == null);
		return count[0];
	}

	@Override
	public <E> Iterable<SimpleEntity<? extends E>> query(EntityQuery<E> query, Object prepared,
		Consumer<Iterable<SimpleEntity<? extends E>>> onAsyncComplete, Consumer<EntityOperationException> onError)
			throws EntityOperationException {
		StringWriter sw = new StringWriter();
		long requestId = theRequestIdGenerator.getAndIncrement();
		JsonSerialWriter jsw = new JsonStreamWriter(sw);
		try {
			jsw.startObject();
			jsw.startProperty("id").writeNumber(requestId);
			jsw.startProperty("sync").writeBoolean(false);
			jsw.startProperty("action").writeString("query");
			if (prepared != null) {
				jsw.startProperty("prepared");
				serializePrepared(jsw, prepared);
			}
			jsw.startProperty("type").writeString(query.getEntityType().getName());
			if (query instanceof ConfigurableQuery) {
				ConfigurableQuery<?> cq = (ConfigurableQuery<?>) query;
				jsw.startProperty("selection");
				serializeCondition(jsw, cq.getSelection());
			} else {
				jsw.startProperty("variables").startObject();
				PreparedQuery<?> pq = (PreparedQuery<?>) query;
				for (int v = 0; v < pq.getVariableValues().keySize(); v++) {
					jsw.startProperty(pq.getVariableValues().keySet().get(v));
					serialize(jsw, pq.getVariableValues().get(v));
				}
				jsw.endObject(); // End variables
			}
			jsw.startProperty("load").startObject();
			for (int f = 0; f < query.getFieldLoadTypes().keySize(); f++) {
				if (query.getFieldLoadTypes().get(f) != null)
					jsw.startProperty(query.getFieldLoadTypes().keySet().get(f)).writeString(query.getFieldLoadTypes().get(f).name());
			}
			jsw.endObject(); // End loading
			jsw.endObject(); // End request
		} catch (IOException e) {
			// Should not happen--writing to a String
		}
		List<SimpleEntity<? extends E>> entities = new ArrayList<>();
		sendRequest(sw.toString(), requestId, jsr -> {
			String propName = jsr.getNextProperty();
			if (!propName.equals("results"))
				throw new EntityOperationException("Could not decode response--results property expected but found " + propName);
			jsr.startArray();
			SimpleEntity<? extends E> entity = (SimpleEntity<? extends E>) parseEntity(jsr);
			while (entity != null) {
				entities.add(entity);
				entity = (SimpleEntity<? extends E>) parseEntity(jsr);
			}
			if (onAsyncComplete != null)
				onAsyncComplete.accept(entities);
		}, onError, onAsyncComplete == null);
		return entities;
	}

	@Override
	public <E> long update(EntityUpdate<E> update, Object prepared, LongConsumer onAsyncComplete,
		Consumer<EntityOperationException> onError) throws EntityOperationException {
		StringWriter sw = new StringWriter();
		long requestId = theRequestIdGenerator.getAndIncrement();
		JsonSerialWriter jsw = new JsonStreamWriter(sw);
		try {
			jsw.startObject();
			jsw.startProperty("id").writeNumber(requestId);
			jsw.startProperty("sync").writeBoolean(false);
			jsw.startProperty("action").writeString("update");
			if (prepared != null) {
				jsw.startProperty("prepared");
				serializePrepared(jsw, prepared);
			}
			jsw.startProperty("type").writeString(update.getEntityType().getName());
			if (update instanceof ConfigurableUpdate) {
				ConfigurableUpdate<?> cu = (ConfigurableUpdate<?>) update;
				jsw.startProperty("selection");
				serializeCondition(jsw, cu.getSelection());
				jsw.startProperty("fields").startObject();
				for (int f = 0; f < cu.getEntityType().getFields().keySize(); f++) {
					Object value = cu.getFieldValues().get(f);
					if (value != EntityUpdate.NOT_SET) {
						jsw.startProperty(cu.getEntityType().getFields().get(f).getName());
						serialize(jsw, value);
					}
				}
				jsw.endObject(); // End fields
			} else {
				jsw.startProperty("variables").startObject();
				PreparedUpdate<?> pu = (PreparedUpdate<?>) update;
				for (int v = 0; v < pu.getVariableValues().keySize(); v++) {
					jsw.startProperty(pu.getVariableValues().keySet().get(v));
					serialize(jsw, pu.getVariableValues().get(v));
				}
				jsw.endObject(); // End variables
			}
			jsw.endObject(); // End request
		} catch (IOException e) {
			// Should not happen--writing to a String
		}
		long[] count = new long[1];
		sendRequest(sw.toString(), requestId, jsr -> {
			String propName = jsr.getNextProperty();
			if (!propName.equals("updateCount"))
				throw new EntityOperationException("Could not decode response--updateCount property expected but found " + propName);
			count[0] = jsr.parseNumber().longValue();
			if (onAsyncComplete != null)
				onAsyncComplete.accept(count[0]);
		}, onError, onAsyncComplete == null);
		return count[0];
	}

	@Override
	public <E> long delete(EntityDeletion<E> delete, Object prepared, LongConsumer onAsyncComplete,
		Consumer<EntityOperationException> onError) throws EntityOperationException {
		StringWriter sw = new StringWriter();
		long requestId = theRequestIdGenerator.getAndIncrement();
		JsonSerialWriter jsw = new JsonStreamWriter(sw);
		try {
			jsw.startObject();
			jsw.startProperty("id").writeNumber(requestId);
			jsw.startProperty("sync").writeBoolean(false);
			jsw.startProperty("action").writeString("update");
			if (prepared != null) {
				jsw.startProperty("prepared");
				serializePrepared(jsw, prepared);
			}
			jsw.startProperty("type").writeString(delete.getEntityType().getName());
			if (delete instanceof ConfigurableDeletion) {
				ConfigurableDeletion<?> cd = (ConfigurableDeletion<?>) delete;
				jsw.startProperty("selection");
				serializeCondition(jsw, cd.getSelection());
			} else {
				jsw.startProperty("variables").startObject();
				PreparedDeletion<?> pd = (PreparedDeletion<?>) delete;
				for (int v = 0; v < pd.getVariableValues().keySize(); v++) {
					jsw.startProperty(pd.getVariableValues().keySet().get(v));
					serialize(jsw, pd.getVariableValues().get(v));
				}
				jsw.endObject(); // End variables
			}
			jsw.endObject(); // End request
		} catch (IOException e) {
			// Should not happen--writing to a String
		}
		long[] count = new long[1];
		sendRequest(sw.toString(), requestId, jsr -> {
			String propName = jsr.getNextProperty();
			if (!propName.equals("deleteCount"))
				throw new EntityOperationException("Could not decode response--deleteCount property expected but found " + propName);
			count[0] = jsr.parseNumber().longValue();
			if (onAsyncComplete != null)
				onAsyncComplete.accept(count[0]);
		}, onError, onAsyncComplete == null);
		return count[0];
	}

	@Override
	public Observable<List<EntityChange<?>>> changes() {
		return theChanges.readOnly();
	}

	@Override
	public List<Fulfillment<?>> loadEntityData(List<EntityLoadRequest<?>> loadRequests, Consumer<List<Fulfillment<?>>> onComplete,
		Consumer<EntityOperationException> onError) throws EntityOperationException {
		StringWriter sw = new StringWriter();
		long requestId = theRequestIdGenerator.getAndIncrement();
		JsonSerialWriter jsw = new JsonStreamWriter(sw);
		try {
			jsw.startObject();
			jsw.startProperty("id").writeNumber(requestId);
			jsw.startProperty("sync").writeBoolean(false);
			jsw.startProperty("action").writeString("load");
			jsw.startProperty("loadRequests").startArray();
			for(EntityLoadRequest<?> request : loadRequests){
				jsw.startObject();
				jsw.startProperty("type").writeString(request.getType().getName());
				jsw.startProperty("entities").startArray();
				for (EntityIdentity<?> entity : request.getEntities())
					serializeIdentity(jsw, entity);
				jsw.endArray();
				jsw.startProperty("fields").startArray();
				for (EntityValueAccess<?, ?> field : request.getFields())
					serializeField(jsw, field);
				jsw.endArray();
				if (request.getChange() != null) {
					jsw.startProperty("change");
					serializeChangeObject(jsw, request.getChange().getCustomData());
				}
				jsw.endObject();
			}
			jsw.endArray();
			jsw.endObject(); // End request
		} catch (IOException e) {
			// Should not happen--writing to a String
		}
		List<Fulfillment<?>> fulfillment = new ArrayList<>(loadRequests.size());
		sendRequest(sw.toString(), requestId, jsr -> {
			String propName = jsr.getNextProperty();
			if (!propName.equals("fulfillment"))
				throw new EntityOperationException("Could not decode response--fulfillment property expected but found " + propName);
			jsr.startArray();
			while (fulfillment.size() < loadRequests.size()) {
				EntityLoadRequest<?> req = loadRequests.get(fulfillment.size());
				List<QuickMap<String, Object>> results = new ArrayList<>(req.getEntities().size());
				jsr.startArray();
				while (results.size() < req.getEntities().size()) {
					jsr.startObject();
					results.add(parseFields(jsr, req.getEntities().get(results.size()).getEntityType()));
					jsr.endObject(null);
				}
				jsr.endArray(null);
				fulfillment.add(new Fulfillment<>(req, results));
			}
			jsr.endArray(null);
			if (onComplete != null)
				onComplete.accept(fulfillment);
		}, onError, onComplete == null);
		return fulfillment;
	}

	/**
	 * This method reads from the given reader, parsing responses to entity operation requests and changes to the data source. When no
	 * content is available, this method blocks. It should be called from a thread that does not need to do anything else while the data
	 * source is connected.
	 *
	 * @param input The reader from the remote {@link JsonEntityProviderServer server}
	 * @throws IOException If an error occurs reading the input
	 * @throws SAJParser.ParseException If non-JSON content is encountered in the reader
	 */
	public void connect(Reader input) throws IOException, SAJParser.ParseException {
		JsonSerialReader jsr = new JsonSerialReader(input);
		JsonParseItem item = jsr.getNextItem(true, false);
		while (item != null) {
			if (item instanceof JsonSerialReader.ObjectItem && ((JsonSerialReader.ObjectItem) item).isBegin()) {
				StructState state = jsr.save();
				try {
					String initialProperty = jsr.getNextProperty();
					if (initialProperty.equals("id")) {
						Object requestId = jsr.parseNext(false);
						if (requestId == JsonSerialReader.NULL)
							changes(jsr);
						else
							theRequests.remove(requestId).accept(jsr);
					} else
						System.err.println("id property expected in input item, not " + initialProperty);
				} finally {
					jsr.endObject(state);
				}
			} else {
				System.err.println("Unrecognized content in stream: " + item);
				if (item instanceof JsonSerialReader.ArrayItem)
					jsr.endArray(null);
			}
			item = jsr.getNextItem(true, false);
		}
	}

	private void serializePrepared(JsonSerialWriter jsw, Object prepared) throws IOException {
		// TODO
	}

	private void serialize(JsonSerialWriter jsw, Object value) throws IOException {
		int todo = todo; // TODO
	}

	private void serializeIdentity(JsonSerialWriter jsw, EntityIdentity<?> id) throws IOException {
		jsw.startObject()//
		.startProperty("type").writeString(id.getEntityType().getName())//
		.startProperty("fields").startObject();
		for (int i = 0; i < id.getFields().keySize(); i++) {
			jsw.startProperty(id.getEntityType().getIdentityFields().get(i).getName());
			serialize(jsw, id.getFields().get(i));
		}
		jsw.endObject().endObject();
	}

	private void serializeChangeObject(JsonSerialWriter jsw, Object changeObject) throws IOException {
		int todo = todo; // TODO
	}

	SimpleEntity<?> parseEntity(JsonSerialReader jsr) throws IOException, SAJParser.ParseException {
		JsonParseItem next = jsr.getNextItem(true, false);
		if (next instanceof JsonSerialReader.ObjectItem && ((JsonSerialReader.ObjectItem) next).isBegin()) {
			String prop = jsr.getNextProperty();
			if (!prop.equals("type")) {
				System.err.println("type attribute expected, not " + prop);
				return null;
			}
			String typeName = jsr.parseString();
			ObservableEntityType<?> type = theEntitySet.getEntityType(typeName);
			QuickMap<String, Object> fields = parseFields(jsr, type);
			jsr.endObject(null);
			EntityIdentity.Builder<?> idBuilder = type.buildId();
			for (int i = 0; i < type.getIdentityFields().keySize(); i++)
				idBuilder.with(i, fields.get(type.getIdentityFields().get(i).getIndex()));
			return new SimpleEntity<>(idBuilder.build(), fields);
		} else
			return null;
	}

	QuickMap<String, Object> parseFields(JsonSerialReader jsr, ObservableEntityType<?> type) throws IOException, SAJParser.ParseException {
		QuickMap<String, Object> fields = type.getFields().keySet().createMap().fill(EntityUpdate.NOT_SET);
		String prop = jsr.getNextProperty();
		while (prop != null) {
			int fieldIndex = fields.keySet().indexOf(prop);
			fields.put(fieldIndex, parse(jsr, type.getFields().get(fieldIndex).getFieldType()));
		}
		return fields;
	}

	Object parse(JsonSerialReader jsr, TypeToken<?> type) throws IOException, SAJParser.ParseException {}

	<E> EntityIdentity<? extends E> parseIdentity(JsonSerialReader jsr, ObservableEntityType<E> type)
		throws IOException, SAJParser.ParseException {
		JsonParseItem next = jsr.getNextItem(true, false);
		if (next instanceof JsonSerialReader.ObjectItem && ((JsonSerialReader.ObjectItem) next).isBegin()) {
			String prop = jsr.getNextProperty();
			if (!prop.equals("type")) {
				System.err.println("type attribute expected, not " + prop);
				return null;
			}
			String typeName = jsr.parseString();
			ObservableEntityType<? extends E> subType = (ObservableEntityType<? extends E>) theEntitySet.getEntityType(typeName);
			EntityIdentity.Builder<? extends E> idBuilder = subType.buildId();
			prop = jsr.getNextProperty();
			while (prop != null) {
				int fieldIndex = subType.getIdentityFields().keyIndex(prop);
				idBuilder.with(fieldIndex, parse(jsr, type.getIdentityFields().get(fieldIndex).getFieldType()));
			}
			jsr.endObject(null);
			return idBuilder.build();
		} else
			return null;

	}

	private void serializeCondition(JsonSerialWriter jsw, EntityCondition<?> condition) throws IOException {
		if (condition instanceof EntityCondition.All)
			jsw.writeString("all");
		else if (condition instanceof EntityCondition.LiteralCondition) {
			EntityCondition.LiteralCondition<?, ?> lit = (EntityCondition.LiteralCondition<?, ?>) condition;
			jsw.startObject()//
			.startProperty("field");
			serializeField(jsw, lit.getField());
			jsw.startProperty("compare").writeString("" + lit.getSymbol())//
			.startProperty("value");
			serialize(jsw, lit.getValue());
			jsw.endObject();
		} else if (condition instanceof EntityCondition.VariableCondition) {
			EntityCondition.VariableCondition<?, ?> vbl = (EntityCondition.VariableCondition<?, ?>) condition;
			jsw.startObject()//
			.startProperty("field");
			serializeField(jsw, vbl.getField());
			jsw.startProperty("compare").writeString("" + vbl.getSymbol())//
			.startProperty("variable").writeString(vbl.getVariable().getName());
			jsw.endObject();
		} else if (condition instanceof EntityCondition.CompositeCondition) {
			EntityCondition.CompositeCondition<?> cc = (EntityCondition.CompositeCondition<?>) condition;
			jsw.startObject().startProperty("composite").writeString(cc instanceof EntityCondition.OrCondition ? "OR" : "AND");
			jsw.startProperty("components").startArray();
			for (EntityCondition<?> component : cc.getConditions())
				serializeCondition(jsw, component);
			jsw.endArray().endObject();
		} else
			throw new IllegalArgumentException("Unrecognized condition type " + condition.getClass().getName());
	}

	private void serializeField(JsonSerialWriter jsw, EntityValueAccess<?, ?> field) throws IOException {
		if (field instanceof ObservableEntityFieldType)
			jsw.writeString(((ObservableEntityFieldType<?, ?>) field).getName());
		else {
			StringBuilder str = new StringBuilder();
			for (ObservableEntityFieldType<?, ?> f : ((EntityChainAccess<?, ?>) field).getFieldSequence()) {
				if (str.length() > 0)
					str.append('.');
				str.append(f.getName());
			}
			jsw.writeString(str.toString());
		}
	}

	private void sendRequest(String request, long requestId, JsonEntityAction fulfill, Consumer<EntityOperationException> onError,
		boolean sync) throws EntityOperationException {
		Object monitor = sync ? new Object() : null;
		EntityOperationException[] ex = sync ? new EntityOperationException[1] : null;
		theRequests.put(requestId, jsr -> {
			try {
				fulfill.accept(jsr);
			} catch (EntityOperationException e) {
				if (sync)
					ex[0] = e;
				else
					onError.accept(e);
			}
			if (sync) {
				synchronized (monitor) {
					monitor.notify();
				}
			}
		});
		synchronized (theConnectionOutput) {
			try {
				theConnectionOutput.write(request);
			} catch (IOException e) {
				if (sync)
					throw new EntityOperationException("Could not send request", e);
				else
					onError.accept(new EntityOperationException("Could not send request", e));
			}
		}
		if (sync) {
			try {
				synchronized (monitor) {
					monitor.wait();
				}
				if (ex[0] != null)
					throw ex[0];
			} catch (InterruptedException e) {
				if (sync)
					throw new EntityOperationException("Interrupted", e);
				else
					onError.accept(new EntityOperationException("Interrupted", e));
			}
		}
	}

	void changes(JsonSerialReader jsr) throws IOException, SAJParser.ParseException {
		String prop = jsr.getNextProperty();
		if (!prop.equals("changes")) {
			System.err.println("Could not deserialize changes--changes property expected but encountered " + prop);
			return;
		}
		jsr.startArray();
		List<EntityChange<?>> changes = new ArrayList<>();
		JsonParseItem next = jsr.getNextItem(true, false);
		changeLoop:
			while (next instanceof JsonSerialReader.ObjectItem && ((JsonSerialReader.ObjectItem) next).isBegin()) {
				prop = jsr.getNextProperty();
				if (!prop.equals("changeID")) {
					System.err.println("Could not deserialize changes--changeID property expected but encountered " + prop);
					jsr.endObject(null);
					continue;
				}
				String changeId = jsr.parseString();
				prop = jsr.getNextProperty();
				if (!prop.equals("changeType")) {
					System.err.println("Could not deserialize changes--changeType property expected but encountered " + prop);
					jsr.endObject(null);
					continue;
				}
				String changeType = jsr.parseString();
				prop = jsr.getNextProperty();
				if (!prop.equals("entityType")) {
					System.err.println("Could not deserialize changes--entityType property expected but encountered " + prop);
					jsr.endObject(null);
					continue;
				}
				String entityType = jsr.parseString();
				ObservableEntityType<?> type = theEntitySet.getEntityType(entityType);
				prop = jsr.getNextProperty();
				if (!prop.equals("time")) {
					System.err.println("Could not deserialize changes--time property expected but encountered " + prop);
					jsr.endObject(null);
					continue;
				}
				Instant time = Instant.ofEpochMilli(QommonsUtils.parse(jsr.parseString()));
				switch (changeType) {
				case "add":
				case "delete":
				case "field-set":
					prop = jsr.getNextProperty();
					if (!prop.equals("entities")) {
						System.err.println("Could not deserialize changes--entities property expected but encountered " + prop);
						jsr.endObject(null);
						continue;
					}
					BetterList<EntityIdentity<?>> entities = BetterTreeList.<EntityIdentity<?>> build().safe(false).build();
					EntityIdentity<?> entity = parseIdentity(jsr, type);
					while (entity != null) {
						entities.add(entity);
						entity = parseIdentity(jsr, type);
					}
					switch (changeType) {
					case "add":
						changes
						.add(new EntityChange.EntityExistenceChange<>((ObservableEntityType<Object>) type, time, true, entities, changeId));
						break;
					case "delete":
						changes.add(
							new EntityChange.EntityExistenceChange<>((ObservableEntityType<Object>) type, time, false, entities, changeId));
						break;
					case "field-set":
						prop = jsr.getNextProperty();
						if (!prop.equals("fields")) {
							System.err.println("Could not deserialize changes--entities property expected but encountered " + prop);
							jsr.endObject(null);
							continue;
						}
						List<FieldChange<?, ?>> fields = new ArrayList<>();
						jsr.startArray();
						next = jsr.getNextItem(true, false);
						while (next instanceof JsonSerialReader.ObjectItem && ((JsonSerialReader.ObjectItem) next).isBegin()) {
							prop = jsr.getNextProperty();
							if (!prop.equals("field")) {
								System.err.println("field attribute expected, not " + prop);
								jsr.endObject(null);
								jsr.endObject(null);
								continue changeLoop;
							}
							ObservableEntityFieldType<?, ?> field = type.getFields().get(jsr.parseString());
							prop = jsr.getNextProperty();
							if (!prop.equals("old-values")) {
								System.err.println("old-values attribute expected, not " + prop);
								jsr.endObject(null);
								jsr.endObject(null);
								continue changeLoop;
							}
							jsr.startArray();
							List<Object> oldValues = new ArrayList<>(entities.size());
							for (int i = 0; i < entities.size(); i++)
								oldValues.add(parse(jsr, field.getFieldType()));
							jsr.endArray(null);
							prop = jsr.getNextProperty();
							if (!prop.equals("new-value")) {
								System.err.println("new-value attribute expected, not " + prop);
								jsr.endObject(null);
								jsr.endObject(null);
								continue changeLoop;
							}
							Object newValue = parse(jsr, field.getFieldType());
							fields.add(new FieldChange<>((ObservableEntityFieldType<?, Object>) field, oldValues, newValue));
							next = jsr.getNextItem(true, false);
						}
						jsr.endArray(null);
						changes.add(new EntityChange.EntityFieldValueChange<>((ObservableEntityType<Object>) type, time, entities,
							(List<FieldChange<Object, ?>>) (List<?>) fields, changeId));
						break;
					}
					break;
				case "collection-add":
				case "collection-remove":
				case "collection-update":
				case "map-add":
				case "map-remove":
				case "map-update":
				default:
					System.err.println("Change type " + changeType + " unsupported");
				}
				jsr.endObject(null);
				next = jsr.getNextItem(true, false);
			}
		jsr.endArray(null);
	}

	interface JsonEntityAction {
		void accept(JsonSerialReader reader) throws IOException, SAJParser.ParseException, EntityOperationException;
	}

	interface JsonAction {
		void accept(JsonSerialReader reader) throws IOException, SAJParser.ParseException;
	}
}
