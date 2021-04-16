package org.observe.util;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Assert;
import org.junit.Test;
import org.observe.util.CsvEntitySet.EntityIterator;
import org.qommons.TestHelper;
import org.qommons.TestHelper.Testable;
import org.qommons.collect.BetterHashMap;
import org.qommons.collect.BetterSortedMap;
import org.qommons.collect.QuickSet.QuickMap;
import org.qommons.ex.CheckedExceptionWrapper;
import org.qommons.io.FileUtils;
import org.qommons.io.Format;
import org.qommons.io.TextParseException;
import org.qommons.tree.BetterTreeList;
import org.qommons.tree.BetterTreeMap;

import com.google.common.reflect.TypeToken;

/** Tests {@link CsvEntitySet} */
public class CsvEntitySetTest {
	static File setup() {
		File directory = new File(System.getProperty("user.home") + "/" + CsvEntitySetTest.class.getSimpleName());
		FileUtils.delete(directory, null);
		return directory;
	}

	/**
	 * @param directory The directory to create the entity set in
	 * @return The new entity set
	 * @throws IOException If the entity set could not be created
	 */
	public static CsvEntitySet createSimpleEntitySet(File directory) throws IOException {
		CsvEntitySet entitySet = new CsvEntitySet(directory, directory);
		return initSimpleEntitySet(entitySet);
	}

	/**
	 * Initializes an entity set with an entity type
	 * 
	 * @param entitySet The entity set
	 * @return The entity set
	 * @throws IOException If the entity type could not be added
	 */
	public static CsvEntitySet initSimpleEntitySet(CsvEntitySet entitySet) throws IOException {
		entitySet.addEntityType("test1", BetterHashMap.build().<String, TypeToken<?>> buildMap()//
			.with("id", TypeTokens.get().LONG)//
			.with("name", TypeTokens.get().STRING)//
			.with("values", new TypeToken<List<Integer>>() {
			}), //
			Arrays.asList("id")).setFormat("values", new Format.ListFormat<>(Format.INT, ",", null));
		entitySet.setTargetFileSize(10 * 1024);
		return entitySet;
	}

	/**
	 * Adds an entity to the "type1" entity type
	 * 
	 * @param entitySet The entity set to add the entity to
	 * @param id The ID for the new entity
	 * @return The new entity
	 * @throws IOException If the entity could not be added
	 */
	public static QuickMap<String, Object> addTestEntity(CsvEntitySet entitySet, int id) throws IOException {
		QuickMap<String, Object> entity = entitySet.getEntityType("test1").create(false)//
			.with("id", (long) id)//
			.with("name", "Entity " + id)//
			.with("values", BetterTreeList.build().safe(false).build()//
				.with(id, (id + 1) * 2, (id + 1) * 3, (id + 1) * 4, (id + 1) * 5))//
			;
		Assert.assertFalse(entitySet.update("test1", entity, true));
		return entity;
	}

	/** Tests basic CRUD functionality */
	@Test
	public void testBasic() {
		File dir = setup();
		CsvEntitySet entitySet = null;
		try {
			entitySet = createSimpleEntitySet(dir);
			for (int i = 0; i < 5; i++)
				addTestEntity(entitySet, i * 10);

			entitySet.close();
			entitySet = null;
			entitySet = new CsvEntitySet(dir, dir);
			EntityIterator getter = entitySet.get("test1");
			for (int i = 0; i < 5; i++) {
				QuickMap<String, Object> entity = getter.getNext();
				Assert.assertNotNull("" + i, entity);
				Assert.assertEquals(Long.valueOf(i * 10), entity.get("id"));
				Assert.assertEquals("Entity " + (i * 10), entity.get("name"));
				Assert.assertEquals(Arrays.asList(i * 10, (i * 10 + 1) * 2, (i * 10 + 1) * 3, (i * 10 + 1) * 4, (i * 10 + 1) * 5),
					entity.get("values"));
				Assert.assertEquals(entity, entitySet.get("test1", entitySet.getEntityType("test1").getFields().keySet().createMap()//
					.with("id", Long.valueOf(i * 10))));
			}
			Assert.assertNull(getter.getNext());
			getter.close();

			addTestEntity(entitySet, 35);
			Assert.assertTrue(entitySet.delete("test1", entitySet.getEntityType("test1").getFields().keySet().createMap()//
				.with("id", 40L)));
			Assert.assertNull(entitySet.get("test1", entitySet.getEntityType("test1").getFields().keySet().createMap()//
				.with("id", 40L)));
			Assert.assertTrue(entitySet.update("test1", entitySet.getEntityType("test1").getFields().keySet().createMap()//
				.with("id", 30L)//
				.with("name", "Entity 30B")//
				.with("values", Arrays.asList(0))//
				, false));
			getter = entitySet.get("test1");
			for (int i = 0; i < 5; i++) {
				QuickMap<String, Object> entity = getter.getNext();
				Assert.assertNotNull("" + i, entity);
				if (i == 4) {
					Assert.assertEquals(35L, entity.get("id"));
					Assert.assertEquals("Entity 35", entity.get("name"));
					Assert.assertEquals(Arrays.asList(35, (35 + 1) * 2, (35 + 1) * 3, (35 + 1) * 4, (35 + 1) * 5), entity.get("values"));
				} else {
					Assert.assertEquals(Long.valueOf(i * 10), entity.get("id"));
					if (i < 3) {
						Assert.assertEquals("Entity " + (i * 10), entity.get("name"));
						Assert.assertEquals(Arrays.asList(i * 10, (i * 10 + 1) * 2, (i * 10 + 1) * 3, (i * 10 + 1) * 4, (i * 10 + 1) * 5),
							entity.get("values"));
					} else {
						Assert.assertEquals("Entity " + (i * 10) + "B", entity.get("name"));
						Assert.assertEquals(Arrays.asList(0), entity.get("values"));
					}
				}
				Assert.assertEquals(entity, entitySet.get("test1", entitySet.getEntityType("test1").getFields().keySet().createMap()//
					.with("id", entity.get("id"))));
			}
			Assert.assertNull(getter.getNext());
			getter.close();
		} catch (IOException | TextParseException e) {
			throw new CheckedExceptionWrapper(e);
		} finally {
			if (entitySet != null) {
				try {
					entitySet.close();
				} catch (IOException e) {
					throw new CheckedExceptionWrapper(e);
				}
			}
			FileUtils.delete(dir, null);
		}
	}

	/** Randomly tests basic CRUD functionality */
	@Test
	public void testBasicRandom() {
		TestHelper.createTester(CsvEntityTestable.class).revisitKnownFailures(true).withDebug(true).withFailurePersistence(true)
		.withMaxProgressInterval(Duration.ofSeconds(10))//
		.withRandomCases(3).withPlacemarks("action").execute().throwErrorIfFailed();
	}

	static class CsvEntityTestable implements Testable {
		private static final int OPS = 10_000;
		private static final int PROGRESS = OPS / 100;
		private static final int PERCENT = OPS / 10;

		@Override
		public void accept(TestHelper helper) {
			File dir = setup();
			CsvEntitySet[] entitySet = new CsvEntitySet[1];
			try {
				entitySet[0] = createSimpleEntitySet(dir);
			} catch (IOException e) {
				throw new CheckedExceptionWrapper(e);
			}
			try {
				BetterSortedMap<Long, QuickMap<String, Object>> existing = BetterTreeMap.build(Long::compare).safe(false).buildMap();
				for (int i = 0; i < OPS; i++) {
					if (i != 0 && i % PROGRESS == 0) {
						if (i % PERCENT == 0)
							System.out.print(i / PROGRESS);
						else
							System.out.print('.');
						System.out.flush();
					}
					helper.createAction()//
					.or(10, () -> { // Add entity
						long newId = helper.getAnyLong();
						if (helper.isReproducing())
							System.out.println("Adding " + newId + ": " + (existing.size() + 1));
						while (existing.containsKey(newId)) {
							newId = helper.getAnyLong();
						}
						try {
							String name = helper.getAlphaNumericString(5, 10);
							List<Integer> values = new ArrayList<>();
							int valueSize = helper.getInt(0, 5);
							for (int j = 0; j < valueSize; j++)
								values.add(helper.getAnyInt());
							QuickMap<String, Object> newEntity = entitySet[0].getEntityType("test1").create(false)//
								.with("id", newId)//
								.with("name", name)//
								.with("values", values);
							existing.put(newId, newEntity);
							Assert.assertFalse(entitySet[0].update("test1", newEntity, true));
							Assert.assertEquals(newEntity,
								entitySet[0].get("test1", entitySet[0].getEntityType("test1").getFields().keySet().createMap()//
									.with("id", newId)));
							Assert.assertEquals(existing.size(), entitySet[0].count("test1"));
						} catch (IOException | TextParseException e) {
							throw new CheckedExceptionWrapper(e);
						}
					}).or(5, () -> { // Get entity
						if (existing.isEmpty())
							return;
						long id = existing.keySet().get(helper.getInt(0, existing.size()));
						if (helper.isReproducing())
							System.out.println("Get " + id);
						try {
							QuickMap<String, Object> retrieved = entitySet[0].get("test1",
								entitySet[0].getEntityType("test1").getFields().keySet().createMap()//
								.with("id", id));
							Assert.assertEquals(existing.get(id), retrieved);
						} catch (IOException | TextParseException e) {
							throw new CheckedExceptionWrapper(e);
						}
					}).or(0.1, () -> { // Iterate through all entities
						if (helper.isReproducing())
							System.out.println("Get all");
						Set<Long> iterated = new HashSet<>();
						try (EntityIterator iter = entitySet[0].get("test1")) {
							for (QuickMap<String, Object> retrieved = iter.getNext(); retrieved != null; retrieved = iter.getNext()) {
								Long id = (Long) retrieved.get("id");
								helper.placemark();
								Assert.assertTrue(iterated.add(id));
								Assert.assertEquals(existing.get(id), retrieved);
							}
							Assert.assertEquals(existing.size(), iterated.size());
						} catch (IOException | TextParseException e) {
							throw new CheckedExceptionWrapper(e);
						}
					}).or(2, () -> { // Remove existing entity
						if (existing.isEmpty())
							return;
						long id = existing.keySet().get(helper.getInt(0, existing.size()));
						if (helper.isReproducing())
							System.out.println("Removing " + id + ": " + (existing.size() - 1));
						try {
							Assert.assertTrue(entitySet[0].delete("test1", entitySet[0].getEntityType("test1").create(false)//
								.with("id", id)));
							existing.remove(id);
							Assert.assertNull(entitySet[0].get("test1", entitySet[0].getEntityType("test1").create(false)//
								.with("id", id)));
							Assert.assertEquals(existing.size(), entitySet[0].count("test1"));
						} catch (IOException | TextParseException e) {
							throw new CheckedExceptionWrapper(e);
						}
					}).or(1, () -> { // Remove non-existent entity
						long id = helper.getAnyLong();
						while (existing.containsKey(id)) {
							id = helper.getLong(0, 1000);
						}
						if (helper.isReproducing())
							System.out.println("Not removing " + id);
						try {
							Assert.assertFalse(entitySet[0].delete("test1", entitySet[0].getEntityType("test1").create(false)//
								.with("id", id)));
							Assert.assertEquals(existing.size(), entitySet[0].count("test1"));
						} catch (IOException e) {
							throw new CheckedExceptionWrapper(e);
						}
					}).or(2, () -> { // Modify entity name
						if (existing.isEmpty())
							return;
						Map.Entry<Long, QuickMap<String, Object>> entity = existing.entrySet().get(helper.getInt(0, existing.size()));
						if (helper.isReproducing())
							System.out.println("Renaming " + entity.getKey());
						String newName = helper.getAlphaNumericString(5, 10);
						try {
							Assert.assertTrue(entitySet[0].update("test1", entitySet[0].getEntityType("test1").create(true)//
								.with("id", entity.getKey())//
								.with("name", newName)//
								, false));
							entity.getValue().put("name", newName);
							Assert.assertEquals(entity.getValue(),
								entitySet[0].get("test1", entitySet[0].getEntityType("test1").create(false)//
									.with("id", entity.getKey())));
						} catch (IOException | TextParseException e) {
							throw new CheckedExceptionWrapper(e);
						}
					}).or(1, () -> { // Replace value list
						if (existing.isEmpty())
							return;
						Map.Entry<Long, QuickMap<String, Object>> entity = existing.entrySet().get(helper.getInt(0, existing.size()));
						if (helper.isReproducing())
							System.out.println("Revaluing " + entity.getKey());
						List<Integer> newValues = new ArrayList<>();
						int valueSize = helper.getInt(0, 5);
						for (int j = 0; j < valueSize; j++)
							newValues.add(helper.getAnyInt());
						try {
							Assert.assertTrue(entitySet[0].update("test1", entitySet[0].getEntityType("test1").create(true)//
								.with("id", entity.getKey())//
								.with("values", newValues)//
								, false));
							entity.getValue().put("values", newValues);
							Assert.assertEquals(entity.getValue(),
								entitySet[0].get("test1", entitySet[0].getEntityType("test1").getFields().keySet().createMap()//
									.with("id", entity.getKey())));
						} catch (IOException | TextParseException e) {
							throw new CheckedExceptionWrapper(e);
						}
					}).or(5, () -> { // Add value
						if (existing.isEmpty())
							return;
						Map.Entry<Long, QuickMap<String, Object>> entity = existing.entrySet().get(helper.getInt(0, existing.size()));
						if (helper.isReproducing())
							System.out.println("+value for " + entity.getKey());
						List<Integer> values = (List<Integer>) entity.getValue().get("values");
						int index = helper.getInt(0, values.size() + 1);
						int newValue = helper.getAnyInt();
						values.add(index, newValue);
						try {
							Assert.assertTrue(entitySet[0].update("test1", entitySet[0].getEntityType("test1").create(true)//
								.with("id", entity.getKey())//
								.with("values", values)//
								, false));
							Assert.assertEquals(entity.getValue(),
								entitySet[0].get("test1", entitySet[0].getEntityType("test1").create(false)//
									.with("id", entity.getKey())));
						} catch (IOException | TextParseException e) {
							throw new CheckedExceptionWrapper(e);
						}
					}).or(2, () -> { // Remove value
						if (existing.isEmpty())
							return;
						Map.Entry<Long, QuickMap<String, Object>> entity = existing.entrySet().get(helper.getInt(0, existing.size()));
						if (helper.isReproducing())
							System.out.println("-value for " + entity.getKey());
						List<Integer> values = (List<Integer>) entity.getValue().get("values");
						if (values.isEmpty())
							return;
						int index = helper.getInt(0, values.size());
						values.remove(index);
						try {
							Assert.assertTrue(entitySet[0].update("test1", entitySet[0].getEntityType("test1").create(true)//
								.with("id", entity.getKey())//
								.with("values", values)//
								, false));
							Assert.assertEquals(entity.getValue(),
								entitySet[0].get("test1", entitySet[0].getEntityType("test1").create(false)//
									.with("id", entity.getKey())));
						} catch (IOException | TextParseException e) {
							throw new CheckedExceptionWrapper(e);
						}
					}).or(3, () -> { // Replace value
						if (existing.isEmpty())
							return;
						Map.Entry<Long, QuickMap<String, Object>> entity = existing.entrySet().get(helper.getInt(0, existing.size()));
						if (helper.isReproducing())
							System.out.println("xvalue for " + entity.getKey());
						List<Integer> values = (List<Integer>) entity.getValue().get("values");
						if (values.isEmpty())
							return;
						int index = helper.getInt(0, values.size());
						int newValue = helper.getAnyInt();
						values.set(index, newValue);
						try {
							Assert.assertTrue(entitySet[0].update("test1", entitySet[0].getEntityType("test1").create(true)//
								.with("id", entity.getKey())//
								.with("name", CsvEntitySet.NO_UPDATE)//
								.with("values", values)//
								, false));
							Assert.assertEquals(entity.getValue(),
								entitySet[0].get("test1", entitySet[0].getEntityType("test1").getFields().keySet().createMap()//
									.with("id", entity.getKey())));
						} catch (IOException | TextParseException e) {
							throw new CheckedExceptionWrapper(e);
						}
					})//
					.execute("action");
				}
				System.out.println("Done");
			} finally {
				try {
					entitySet[0].close();
				} catch (IOException e) {
					throw new CheckedExceptionWrapper(e);
				}
			}
		}
	}
}
