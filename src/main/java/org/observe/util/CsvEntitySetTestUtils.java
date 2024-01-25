package org.observe.util;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.qommons.collect.BetterHashMap;
import org.qommons.collect.QuickSet.QuickMap;
import org.qommons.io.Format;
import org.qommons.tree.BetterTreeList;

import com.google.common.reflect.TypeToken;

public class CsvEntitySetTestUtils {

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
		entitySet.addEntityType("test1", BetterHashMap.build().<String, TypeToken<?>> build()//
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
			.with("values", BetterTreeList.build().build()//
				.with(id, (id + 1) * 2, (id + 1) * 3, (id + 1) * 4, (id + 1) * 5))//
		;
		if (entitySet.update("test1", entity, true))
			throw new AssertionError("Entity already existed");
		return entity;
	}
}
