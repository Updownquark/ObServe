package org.observe.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.Reader;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.Function;

import org.qommons.Named;
import org.qommons.StringUtils;
import org.qommons.collect.BetterSortedMap;
import org.qommons.collect.QuickSet.QuickMap;
import org.qommons.io.CsvParser;
import org.qommons.io.Format;
import org.qommons.io.TextParseException;
import org.qommons.tree.BetterTreeMap;

import com.google.common.reflect.TypeToken;

/**
 * <p>
 * A simple data set for entities stored in CSV format.
 * </p>
 *
 * <p>
 * Each entity is represented by at least 1 CSV file in a directory. The header contains the names and types of the fields (space
 * separated?).
 * </p>
 *
 * <p>
 * ID column(s) always first, marked with a special character (e.g. '*').
 * </p>
 *
 * <p>
 * Each row (besides header) in a file is an entity instance. Number of files per type is related to the number of entities (with a range,
 * for tolerance)/hysteresis. An entity's ID is hashed to determine which file it belongs in. Entities within a file are sorted by ascending
 * ID.
 * </p>
 *
 * <p>
 * Entity relationships are NOT handled here, including inheritance or references. References in particular must be handled in front of
 * this--e.g. by converting reference fields to identities.
 * </p>
 *
 * <p>
 * All fields, including non-scalar ones, occupy a single column in a single row in the CSV, formatted to text. Newlines, commas, etc. in
 * formatted values are handled by this class.
 * </p>
 */
public class CsvEntitySet {
	/** A result set that iterates over entities of a particular type and allows certain interactions with the entity set */
	public interface EntityIterator extends AutoCloseable {
		/** @return The entity format that this iterator returns entities of */
		EntityFormat getFormat();

		/**
		 * Parses the identity of the next entity in the data set
		 *
		 * @return The next entity in the data set, with only ID fields filled in, or null if there are no more entities in the data set
		 * @throws IOException If the file could not be read
		 * @throws TextParseException If an error occurred parsing the next entity identity
		 */
		QuickMap<String, Object> getNextId() throws IOException, TextParseException;

		/**
		 * Parses the next entity in the data set
		 *
		 * @return The next entity in the data set, or null if there are no more entities in the data set
		 * @throws IOException If the file could not be read
		 * @throws TextParseException If an error occurred parsing the next entity
		 */
		default QuickMap<String, Object> getNext() throws IOException, TextParseException {
			if (getNextId() == null) {
				return null;
			}
			return getLast();
		}

		/**
		 * Gets the last entity parsed from the data set. If the entity has not been filled in with non-ID fields, these will be missing.
		 *
		 * @return The last entity parsed from the data set, or null if no entities have yet been parsed or if the last entity was
		 *         {@link #delete() deleted}
		 */
		QuickMap<String, Object> getLastId();

		/**
		 * Gets the last entity parsed from the data set. If the entity has not been filled in with non-ID fields, these will be parsed.
		 *
		 * @return The last entity parsed from the data set, or null if no entities have yet been parsed or if the last entity was
		 *         {@link #delete() deleted}
		 * @throws TextParseException If the non-ID fields could not be parsed
		 */
		QuickMap<String, Object> getLast() throws TextParseException;

		/**
		 * Deletes the most recently parsed entity from the data set
		 *
		 * @throws IOException If the file could not be written
		 * @throws IllegalStateException If no entities have yet been parsed or if the last entity was already deleted
		 */
		void delete() throws IOException, IllegalStateException;

		/**
		 * Modifies the most recently parsed entity from the data set. The ID of an entity can never be changed, so the ID fields of the
		 * given entity structure are ignored.
		 *
		 * @param entity An entity whose non-ID values to replace for those of the most recently parsed entity from the data set
		 * @throws IOException If the file could not be written
		 * @throws IllegalStateException If no entities have yet been parsed or if the last entity was already deleted
		 */
		void update(QuickMap<String, Object> entity) throws IOException, IllegalStateException;

		@Override
		void close() throws IOException;
	}

	/** Represents an entity type in the data set, containing all information on how to read and write data for the entity */
	public class EntityFormat implements Named {
		private final String theName;
		private final QuickMap<String, TypeToken<?>> theFields;
		private final int theIdFieldCount;
		private final Format<?>[] theFieldFormats;
		private final List<String> theFieldOrder;
		private final String[] theHeader;
		private boolean isUsing;

		EntityFormat(String name, Map<String, TypeToken<?>> fields, Collection<String> idFields) {
			theName = name;
			theFields = QuickMap.of(fields, StringUtils.DISTINCT_NUMBER_TOLERANT);
			List<String> fieldOrder = new ArrayList<>(theFields.keySize());
			if (!fields.keySet().containsAll(idFields)) {
				throw new IllegalArgumentException("Some ID fields not found: " + idFields + " in " + fields.keySet());
			}
			if (idFields.isEmpty()) {
				throw new IllegalArgumentException("Entities must have at least 1 identity field");
			}
			for (String idField : idFields) {
				if (!Comparable.class.isAssignableFrom(TypeTokens.getRawType(fields.get(idField)))) {
					throw new IllegalArgumentException("ID fields must be Comparable: " + idField + " (" + fields.get(idField) + ")");
				}
			}
			fieldOrder.addAll(idFields);
			for (String field : fields.keySet()) {
				if (!idFields.contains(field)) {
					fieldOrder.add(field);
				}
			}
			theFieldOrder = Collections.unmodifiableList(fieldOrder);
			theIdFieldCount = idFields.size();
			theFieldFormats = new Format[theFields.keySize()];
			theHeader = new String[theFieldOrder.size()];
			for (int f = 0; f < theFieldOrder.size(); f++) {
				int fieldIndex = theFields.keyIndex(theFieldOrder.get(f));
				theHeader[f] = (f < theIdFieldCount ? "*" : "") + theFieldOrder.get(f) + ':' + theFields.get(fieldIndex);
			}
		}

		EntityFormat(String name, QuickMap<String, TypeToken<?>> fields, int idFieldCount, Format<?>[] fieldFormats,
			List<String> fieldOrder, String[] header) {
			theName = name;
			theFields = fields;
			theIdFieldCount = idFieldCount;
			theFieldFormats = fieldFormats;
			theFieldOrder = fieldOrder;
			theHeader = header;
		}

		EntityFormat using() {
			isUsing = true;
			return this;
		}

		void writeHeader(Writer writer) throws IOException {
			for (int f = 0; f < theHeader.length; f++) {
				if (f > 0) {
					writer.append(',');
				}
				writer.write(CsvParser.toCsv(theHeader[f], ','));
			}
			writer.write('\n');
		}

		@Override
		public String getName() {
			return theName;
		}

		/** @return The name/type of all this entity type's fields */
		public QuickMap<String, TypeToken<?>> getFields() {
			return theFields;
		}

		/**
		 * @param fieldIndex The {@link QuickMap#keyIndex(Object) index} of the {@link #getFields() field}
		 * @return The text format that will be used for values of the given field
		 */
		public Format<?> getFieldFormat(int fieldIndex) {
			Format<?> format = theFieldFormats[fieldIndex];
			if (format == null) {
				format = theFormats.getFormat(this, theFields.keySet().get(fieldIndex));
				theFieldFormats[fieldIndex] = format;
			}
			return format;
		}

		/**
		 * @param fieldName The name of the {@link #getFields() field} to set the format for
		 * @param format The text format to use for values of the given field
		 * @return This entity format
		 */
		public EntityFormat setFormat(String fieldName, Format<?> format) {
			if (isUsing) {
				throw new IllegalStateException("This format is already being used--field formats cannot be changed");
			}
			theFieldFormats[theFields.keyIndex(fieldName)] = format;
			return this;
		}

		/** @return The number of identity fields in this type. Identity fields are stored first in {@link #getFieldOrder()} */
		public int getIdFieldCount() {
			return theIdFieldCount;
		}

		/** @return The names of all this entity's fields, in the column order they will be stored in in CSV files */
		public List<String> getFieldOrder() {
			return theFieldOrder;
		}

		/**
		 * @param entity1 The first entity to test
		 * @param entity2 The second entity to test
		 * @return &lt;0 if entity1 would appear before entity2 in the same file, &gt;0 if entity2 would appear before entity1, or 0 if the
		 *         two entities have the same identity
		 */
		public int compareIds(QuickMap<String, Object> entity1, QuickMap<String, Object> entity2) {
			for (int f = 0; f < theIdFieldCount; f++) {
				int fieldIndex = theFields.keyIndex(theFieldOrder.get(f));
				int comp;
				if (entity1.get(fieldIndex) == null) {
					if (entity2.get(fieldIndex) == null) {
						comp = 0;
					} else {
						comp = -1;
					}
				} else if (entity2.get(fieldIndex) == null) {
					comp = 1;
				} else {
					comp = ((Comparable<Object>) entity2.get(fieldIndex)).compareTo(entity1.get(fieldIndex));
				}
				if (comp != 0) {
					return comp;
				}
			}
			return 0;
		}

		String[] getHeader() {
			return theHeader;
		}

		void checkHeader(String[] line, File file, CsvParser fileParser) throws TextParseException {
			for (int f = 0; f < line.length; f++) {
				if (!line[f].replaceAll(" ", "").equalsIgnoreCase(theHeader[f].replaceAll(" ", ""))) {
					fileParser.throwParseException(f, 0, "Bad " + theName + " file " + file.getName() + " header");
				}
			}
		}

		boolean parseIds(String[] line, QuickMap<String, Object> entity, CsvParser fileParser, boolean ignoreParseExceptions)
			throws TextParseException {
			for (int f = 0; f < theIdFieldCount; f++) {
				int fieldIndex = theFields.keyIndex(theFieldOrder.get(f));
				try {
					entity.put(fieldIndex, theFieldFormats[fieldIndex].parse(line[f]));
				} catch (ParseException e) {
					if (ignoreParseExceptions) {
						return false;
					}
					fileParser.throwParseException(f, e.getErrorOffset(), e.getMessage(), e);
				}
			}
			return true;
		}

		void parseNonIds(String[] line, QuickMap<String, Object> entity, CsvParser fileParser) throws TextParseException {
			for (int f = theIdFieldCount; f < theFields.keySize(); f++) {
				int fieldIndex = theFields.keyIndex(theFieldOrder.get(f));
				try {
					entity.put(fieldIndex, theFieldFormats[fieldIndex].parse(line[f]));
				} catch (ParseException e) {
					fileParser.throwParseException(f, e.getErrorOffset(), e.getMessage(), e);
				}
			}
		}

		EntityFormat rename(String newName) {
			return new EntityFormat(newName, theFields, theIdFieldCount, theFieldFormats, theFieldOrder, theHeader);
		}

		<F> EntityFormat addField(String fieldName, TypeToken<F> fieldType, Format<F> fieldFormat, boolean id) {
			if (theFields.keySet().contains(fieldName)) {
				throw new IllegalArgumentException("A field named " + theName + "." + fieldName + " already exists");
			}
			Map<String, TypeToken<?>> fields = new LinkedHashMap<>();
			fields.putAll(theFields.asJavaMap());
			fields.put(fieldName, fieldType);
			List<String> idFields;
			if (id) {
				idFields = new ArrayList<>(theIdFieldCount + 1);
				for (int i = 0; i < theIdFieldCount; i++) {
					idFields.add(theFieldOrder.get(i));
				}
				idFields.add(fieldName);
			} else {
				idFields = theFieldOrder.subList(0, theIdFieldCount);
			}
			EntityFormat newFormat = new EntityFormat(theName, fields, idFields);
			if (fieldFormat != null) {
				newFormat.theFieldFormats[newFormat.theFields.keyIndex(fieldName)] = fieldFormat;
			}
			return newFormat;
		}

		EntityFormat removeField(String fieldName) {
			if (!theFields.keySet().contains(fieldName)) {
				throw new IllegalArgumentException("No such field: " + theName + "." + fieldName);
			}
			boolean id = theFieldOrder.indexOf(fieldName) < theIdFieldCount;
			if (id && theIdFieldCount == 1) {
				throw new IllegalArgumentException("Cannot remove sole ID field " + theName + "." + fieldName);
			}
			Map<String, TypeToken<?>> fields = new LinkedHashMap<>();
			fields.putAll(theFields.asJavaMap());
			fields.remove(fieldName);
			List<String> idFields;
			if (id) {
				idFields = new ArrayList<>(theIdFieldCount + 1);
				for (int i = 0; i < theIdFieldCount; i++) {
					idFields.add(theFieldOrder.get(i));
				}
				idFields.remove(fieldName);
			} else {
				idFields = theFieldOrder.subList(0, theIdFieldCount);
			}
			return new EntityFormat(theName, fields, idFields);
		}

		EntityFormat renameField(String oldFieldName, String newFieldName) {
			if (!theFields.keySet().contains(oldFieldName)) {
				throw new IllegalArgumentException("No such field: " + theName + "." + oldFieldName);
			} else if (theFields.keySet().contains(newFieldName)) {
				throw new IllegalArgumentException("A field named " + theName + "." + newFieldName + " already exists");
			}
			Map<String, TypeToken<?>> fields = new LinkedHashMap<>();
			fields.putAll(theFields.asJavaMap());
			fields.put(newFieldName, fields.get(oldFieldName));
			boolean id = theFieldOrder.indexOf(oldFieldName) < theIdFieldCount;
			List<String> idFields;
			if (id) {
				idFields = new ArrayList<>(theIdFieldCount + 1);
				for (int i = 0; i < theIdFieldCount; i++) {
					idFields.add(theFieldOrder.get(i));
				}
				idFields.remove(oldFieldName);
				idFields.add(newFieldName);
			} else {
				idFields = theFieldOrder.subList(0, theIdFieldCount);
			}
			return new EntityFormat(theName, fields, idFields);
		}

		EntityFormat changeFieldId(String fieldName, boolean shouldBeId) {
			if (!theFields.keySet().contains(fieldName)) {
				throw new IllegalArgumentException("No such field: " + theName + "." + fieldName);
			}
			boolean wasID = theFieldOrder.indexOf(fieldName) < theIdFieldCount;
			if (wasID == shouldBeId) {
				return this;
			} else if (!shouldBeId && theIdFieldCount == 1) {
				throw new IllegalArgumentException("Cannot un-identify sole ID field " + theName + "." + fieldName);
			}
			List<String> idFields = new ArrayList<>(theIdFieldCount + (shouldBeId ? 1 : 0));
			for (int i = 0; i < theIdFieldCount; i++) {
				idFields.add(theFieldOrder.get(i));
			}
			if (shouldBeId) {
				idFields.add(fieldName);
			} else {
				idFields.remove(fieldName);
			}
			return new EntityFormat(theName, theFields.asJavaMap(), idFields);
		}
	}

	private final File theDirectory;
	private final Map<String, EntityFormat> theEntityFormats;
	private CsvEntityFormatSet theFormats;
	private long theTargetFileSize;

	/**
	 * @param directory The parent directory in which entities of all types are kept
	 * @throws IOException If an error occurs scanning the directory for entity types
	 */
	public CsvEntitySet(File directory) throws IOException {
		theDirectory = directory;
		theEntityFormats = new LinkedHashMap<>();
		theFormats = new CsvEntityFormatSet();
		theTargetFileSize = 10 * 1024 * 1024;

		// Scan the directory for entities and parse the header info
		if (theDirectory.isDirectory()) {
			for (File entityDir : theDirectory.listFiles()) {
				File[] entityFiles = getEntityFiles(entityDir);
				if (entityFiles.length > 0) {
					try (Reader reader = new BufferedReader(new FileReader(entityFiles[0]))) {
						CsvParser parser = new CsvParser(reader, ',');
						try {
							String[] header = parser.parseNextLine();
							EntityFormat format = parseHeader(entityDir.getName(), header, parser);
							theEntityFormats.put(format.getName(), format);
						} catch (TextParseException e) {
							System.err.println("Bad header for " + entityDir.getName() + "/" + entityFiles[0].getName());
							e.printStackTrace();
						}
					}
				}
			}
		} else {
			theDirectory.mkdirs();
		}
	}

	/**
	 * @return The format set that this entity set uses to fill in field formats that are not {@link EntityFormat#setFormat(String, Format)
	 *         set} explicitly
	 */
	public CsvEntityFormatSet getFormats() {
		return theFormats;
	}

	/**
	 * @param formats The format set that this entity set should use to fill in field formats that are not
	 *        {@link EntityFormat#setFormat(String, Format) set} explicitly
	 * @return This entity set
	 */
	public CsvEntitySet withFormats(CsvEntityFormatSet formats) {
		theFormats = formats;
		return this;
	}

	/** @return All entity types available in this entity set */
	public Map<String, EntityFormat> getEntityTypes() {
		return Collections.unmodifiableMap(theEntityFormats);
	}

	/**
	 * @param typeName The name of the entity type
	 * @return The entity type in this entity set with the given name
	 */
	public EntityFormat getEntityType(String typeName) {
		return theEntityFormats.get(typeName);
	}

	// CRUD

	/**
	 * @param typeName The name of the entity type
	 * @return An iterator for all entities in this set of the given type
	 * @throws IOException If the data could not be read
	 * @throws IllegalArgumentException If no such entity type exists in this entity set
	 */
	public EntityIterator get(String typeName) throws IOException, IllegalArgumentException {
		EntityFormat format = getEntityType(typeName);
		if (format == null) {
			throw new IllegalArgumentException("No such type found: " + typeName);
		}
		File entityDir = new File(theDirectory, typeName);
		File[] entityFiles = getEntityFiles(entityDir);
		return new EntityGetterImpl(format.using(), entityFiles);
	}

	/**
	 * @param typeName The name of the entity type
	 * @param entityId The ID of the entity to find
	 * @return The found entity, or null if no such entity could be found in this entity set
	 * @throws IOException If the data could not be read
	 * @throws TextParseException If the entity data could not be parsed
	 * @throws IllegalArgumentException If no such entity type exists in this entity set
	 */
	public QuickMap<String, Object> get(String typeName, QuickMap<String, Object> entityId)
		throws IOException, TextParseException, IllegalArgumentException {
		EntityFormat format = getEntityType(typeName);
		if (format == null) {
			throw new IllegalArgumentException("No such type found: " + typeName);
		}
		File entityDir = new File(theDirectory, typeName);
		File[] entityFiles = getEntityFiles(entityDir);
		if (entityFiles.length == 0) {
			return null;
		}
		int fileIndex = getFileIndex(format.using(), entityId, entityFiles.length);
		try (EntityGetterImpl getter = new EntityGetterImpl(format, new File[] { entityFiles[fileIndex] })) {
			if (getter.seek(entityId)) {
				return getter.getLast();
			}
		}
		return null;
	}

	/**
	 * @param typeName The name of the entity type
	 * @param entityId The ID of the entity to delete
	 * @return Whether the entity was found and deleted
	 * @throws IOException If an error occurs reading or modifying the data
	 * @throws IllegalArgumentException If no such entity type exists in this entity set
	 */
	public boolean delete(String typeName, QuickMap<String, Object> entityId) throws IOException, IllegalArgumentException {
		EntityFormat format = getEntityType(typeName);
		if (format == null) {
			throw new IllegalArgumentException("No such type found: " + typeName);
		}
		File entityDir = new File(theDirectory, typeName);
		File[] entityFiles = getEntityFiles(entityDir);
		if (entityFiles.length == 0) {
			return false;
		}
		int fileIndex = getFileIndex(format, entityId, entityFiles.length);
		boolean found = false, deleteFile = false;
		try (EntityGetterImpl getter = new EntityGetterImpl(format, new File[] { entityFiles[fileIndex] })) {
			found = getter.seek(entityId);
			if (found) {
				getter.delete();
				if (getIdealFileCount(entityFiles) < entityFiles.length - 1 && getter.getEntriesParsed() == 0) {
					deleteFile = getter.getNextGoodId() == null;
				}
			}
		}
		if (deleteFile) {
			if (!entityFiles[fileIndex].delete()) {
				System.err
				.println(getClass().getSimpleName() + " WARNING: Unable to delete entity file " + entityFiles[fileIndex].getPath());
			}
			fileRemoved(entityFiles[fileIndex]);
			redistributeEntities(format);
		}
		return found;
	}

	/**
	 * @param typeName The name of the entity type
	 * @param entity The entity to modify or insert
	 * @param insertIfNotFound Whether to add the entity to the data set if it does not already exist
	 * @return True if the entity was found in the data set and updated, false otherwise
	 * @throws IOException If an error occurs reading or modifying the data
	 * @throws IllegalArgumentException If no such entity type exists in this entity set
	 */
	public boolean update(String typeName, QuickMap<String, Object> entity, boolean insertIfNotFound)
		throws IOException, IllegalArgumentException {
		EntityFormat format = getEntityType(typeName);
		if (format == null) {
			throw new IllegalArgumentException("No such type found: " + typeName);
		}
		File entityDir = new File(theDirectory, typeName);
		File[] entityFiles = getEntityFiles(entityDir);
		if (entityFiles.length > 0) {
			File file = entityFiles[getFileIndex(format.using(), entity, entityFiles.length)];
			try (EntityGetterImpl getter = new EntityGetterImpl(format, new File[] { file })) {
				boolean found = getter.seek(entity);
				if (found) {
					getter.update(entity);
					return true;
				} else if (getIdealFileCount(entityFiles) <= entityFiles.length + 1) {
					getter.insert(entity);
					return false;
				}
			}
		}
		// Need to add a new file
		File file = new File(entityDir,
			StringUtils.getNewItemName(Arrays.asList(entityFiles), File::getName, typeName, StringUtils.SIMPLE_DUPLICATES) + ".csv");
		try (Writer writer = new FileWriter(file)) {
			format.writeHeader(writer);
		}
		fileAdded(file);
		redistributeEntities(format);
		entityFiles = getEntityFiles(entityDir);
		file = entityFiles[getFileIndex(format.using(), entity, entityFiles.length)];
		try (EntityGetterImpl getter = new EntityGetterImpl(format, new File[] { file })) {
			getter.seek(entity);
			getter.insert(entity);
		}
		return false;
	}

	// Schema changes

	/**
	 * Adds an entity type to this entity set
	 *
	 * @param typeName The name of the type
	 * @param fields The names/types of each field in the entity
	 * @param idFields The names of fields that should be used as identifiers
	 * @return The new entity type
	 * @throws IOException If an empty entity file for the new entity type cannot be written
	 */
	public EntityFormat addEntityType(String typeName, Map<String, TypeToken<?>> fields, Collection<String> idFields) throws IOException {
		if (theEntityFormats.containsKey(typeName)) {
			throw new IllegalArgumentException("Type already exists: " + typeName);
		}
		EntityFormat format = new EntityFormat(typeName, fields, idFields);
		File entityDir = new File(theDirectory, typeName);
		if (!entityDir.exists() && !entityDir.mkdir()) {
			throw new IOException("Could not create entity directory " + entityDir.getPath());
		}
		schemaChanged();
		theEntityFormats.put(typeName, format);
		File file = new File(entityDir, typeName + ".csv");
		try (Writer writer = new FileWriter(file)) {
			format.writeHeader(writer);
		}
		fileAdded(file);
		return format;
	}

	/**
	 * @param entity The entity type to remove
	 * @throws IOException If an error occurs deleting the data for the entity
	 */
	public void removeType(EntityFormat entity) throws IOException {
		if (!theEntityFormats.containsKey(entity.getName())) {
			throw new IllegalArgumentException("No such entity: " + entity.getName());
		}
		removeFile(new File(theDirectory, entity.getName()));
		schemaChanged();
		theEntityFormats.remove(entity.getName());
	}

	/**
	 * @param entity The entity type to modify
	 * @param newName The new name for the entity type
	 * @return The altered entity type
	 * @throws IOException If an error occurs moving the data for the entity
	 */
	public EntityFormat renameType(EntityFormat entity, String newName) throws IOException {
		if (newName.equals(entity.getName())) {
			return entity;
		}
		if (theEntityFormats.containsKey(newName)) {
			throw new IllegalArgumentException("An entity named " + newName + " already exists");
		}
		schemaChanged();
		renameTypeFile(new File(theDirectory, entity.getName()), entity.getName(), newName);
		EntityFormat newFormat = entity.rename(newName);
		theEntityFormats.remove(entity.getName());
		theEntityFormats.put(newName, newFormat);
		return newFormat;
	}

	/**
	 * @param <F> The type of the field
	 * @param entity The entity type to modify
	 * @param fieldName The name of the new field
	 * @param fieldType The type of the new field
	 * @param fieldFormat The format to use for the field (this cannot be set later if any entities of the given type exist and must be
	 *        populated with a value for the field)
	 * @param id Whether the field should be part of the entity's identity
	 * @param value The initial value for all entities of the given type
	 * @return The altered entity type
	 * @throws IOException If an error occurs reading or modifying the data for the entity
	 */
	public <F> EntityFormat addField(EntityFormat entity, String fieldName, TypeToken<F> fieldType, Format<F> fieldFormat, boolean id,
		F value) throws IOException {
		EntityFormat oldEntity = theEntityFormats.get(entity.getName());
		if (oldEntity == null) {
			throw new IllegalArgumentException("No such entity " + entity.getName());
		}
		EntityFormat newEntity = oldEntity.addField(fieldName, fieldType, fieldFormat, id);
		int fieldOrder = newEntity.getFieldOrder().indexOf(fieldName);
		int fieldIndex = newEntity.getFields().keyIndex(fieldName);
		String[] newLine = new String[newEntity.getFieldOrder().size()];
		newLine[fieldOrder] = ((Format<F>) newEntity.getFieldFormat(fieldIndex)).format(value);
		schemaChanged();
		File[] entityFiles = getEntityFiles(new File(theDirectory, oldEntity.getName()));
		try (EntityGetterImpl getter = new EntityGetterImpl(oldEntity, entityFiles)) {
			getter.setNewHeader(newEntity.getHeader());
			for (String[] line = getter.getNextLine(true); line != null; line = getter.getNextLine(true)) {
				newEntity.using();
				System.arraycopy(getter.getLastLine(), 0, newLine, 0, fieldOrder);
				if (fieldOrder < oldEntity.getFieldOrder().size()) {
					System.arraycopy(getter.getLastLine(), fieldOrder, newLine, fieldOrder + 1,
						oldEntity.getFieldOrder().size() - fieldOrder);
				}
				getter.update(newLine);
			}
		} catch (TextParseException e) {
			throw new IllegalStateException("Should not happen", e);
		}
		if (id) { // Identity changed
			redistributeEntities(newEntity);
		}
		return newEntity;
	}

	/**
	 * @param <F> The type of the field
	 * @param entity The entity type to modify
	 * @param fieldName The name of the new field
	 * @param fieldType The type of the new field
	 * @param fieldFormat The format to use for the field (this cannot be set later if any entities of the given type exist and must be
	 *        populated with a value for the field)
	 * @param id Whether the field should be part of the entity's identity
	 * @param value A function to produce initial values for each entity of the given type
	 * @return The altered entity type
	 * @throws IOException If an error occurs reading or modifying the data for the entity
	 */
	public <F> EntityFormat addField(EntityFormat entity, String fieldName, TypeToken<F> fieldType, Format<F> fieldFormat, boolean id,
		Function<QuickMap<String, Object>, F> value) throws IOException {
		EntityFormat oldEntity = theEntityFormats.get(entity.getName());
		if (oldEntity == null) {
			throw new IllegalArgumentException("No such entity " + entity.getName());
		}
		EntityFormat newEntity = oldEntity.addField(fieldName, fieldType, fieldFormat, id);
		int fieldOrder = newEntity.getFieldOrder().indexOf(fieldName);
		int fieldIndex = newEntity.getFields().keyIndex(fieldName);
		String[] newLine = new String[newEntity.getFieldOrder().size()];
		schemaChanged();
		File[] entityFiles = getEntityFiles(new File(theDirectory, oldEntity.getName()));
		try (EntityGetterImpl getter = new EntityGetterImpl(oldEntity, entityFiles)) {
			getter.setNewHeader(newEntity.getHeader());
			for (QuickMap<String, Object> e = getter.getNextGoodId(); e != null; e = getter.getNextGoodId()) {
				System.arraycopy(getter.getLastLine(), 0, newLine, 0, fieldOrder);
				try {
					e = getter.getLast(); // Try to parse the supplemental fields if we can
				} catch (TextParseException ex) {}
				F newValue = value.apply(e);
				newEntity.using();
				newLine[fieldOrder] = ((Format<F>) newEntity.getFieldFormat(fieldIndex)).format(newValue);
				if (fieldOrder < oldEntity.getFieldOrder().size()) {
					System.arraycopy(getter.getLastLine(), fieldOrder, newLine, fieldOrder + 1,
						oldEntity.getFieldOrder().size() - fieldOrder);
				}
				getter.update(newLine);
			}
		}
		if (id) { // Identity changed
			redistributeEntities(newEntity);
		}
		return newEntity;
	}

	/**
	 * @param entity The entity type to modify
	 * @param fieldName The name of the field to remove
	 * @return The altered entity type
	 * @throws IOException If an error occurs reading or modifying the data for the entity
	 */
	public EntityFormat removeField(EntityFormat entity, String fieldName) throws IOException {
		EntityFormat oldEntity = theEntityFormats.get(entity.getName());
		if (oldEntity == null) {
			throw new IllegalArgumentException("No such entity " + entity.getName());
		}
		EntityFormat newEntity = oldEntity.removeField(fieldName);
		int fieldOrder = oldEntity.getFieldOrder().indexOf(fieldName);
		String[] newLine = new String[newEntity.getFieldOrder().size()];
		schemaChanged();
		File[] entityFiles = getEntityFiles(new File(theDirectory, oldEntity.getName()));
		try (EntityGetterImpl getter = new EntityGetterImpl(oldEntity, entityFiles)) {
			getter.setNewHeader(newEntity.getHeader());
			for (String[] line = getter.getNextLine(true); line != null; line = getter.getNextLine(true)) {
				if (fieldOrder > 0) {
					System.arraycopy(getter.getLastLine(), 0, newLine, 0, fieldOrder);
				}
				if (fieldOrder < oldEntity.getFieldOrder().size()) {
					System.arraycopy(getter.getLastLine(), fieldOrder + 1, newLine, fieldOrder,
						oldEntity.getFieldOrder().size() - fieldOrder - 1);
				}
				getter.update(newLine);
			}
		} catch (TextParseException e) {
			throw new IllegalStateException("Should not happen", e);
		}
		if (fieldOrder < oldEntity.getIdFieldCount()) { // Identity changed
			redistributeEntities(newEntity);
		}
		return newEntity;
	}

	/**
	 * @param entity The entity type to modify
	 * @param oldFieldName The current name of the field
	 * @param newFieldName The new name for the field
	 * @return The altered entity type
	 * @throws IOException If an error occurs reading or modifying the data for the entity
	 */
	public EntityFormat renameField(EntityFormat entity, String oldFieldName, String newFieldName) throws IOException {
		if (oldFieldName.equals(newFieldName)) {
			return entity;
		}
		EntityFormat oldEntity = theEntityFormats.get(entity.getName());
		if (oldEntity == null) {
			throw new IllegalArgumentException("No such entity " + entity.getName());
		}
		EntityFormat newEntity = oldEntity.renameField(oldFieldName, newFieldName);
		movedField(oldEntity, newEntity, oldFieldName, newFieldName);
		int oldFieldOrder = oldEntity.getFieldOrder().indexOf(oldFieldName);
		int newFieldOrder = newEntity.getFieldOrder().indexOf(newFieldName);
		if (oldFieldOrder != newFieldOrder && oldFieldOrder < oldEntity.getIdFieldCount()) { // Identity changed
			redistributeEntities(newEntity);
		}
		return newEntity;
	}

	/**
	 * @param entity The entity type to modify
	 * @param fieldName The name of the field
	 * @param shouldBeId Whether the field should be part of the entity's identity
	 * @return The altered entity type
	 * @throws IOException If an error occurs reading or modifying the data for the entity
	 */
	public EntityFormat changeFieldId(EntityFormat entity, String fieldName, boolean shouldBeId) throws IOException {
		EntityFormat oldEntity = theEntityFormats.get(entity.getName());
		if (oldEntity == null) {
			throw new IllegalArgumentException("No such entity " + entity.getName());
		}
		EntityFormat newEntity = oldEntity.changeFieldId(fieldName, shouldBeId);
		if (oldEntity == newEntity) {
			return newEntity;
		}
		schemaChanged();
		movedField(oldEntity, newEntity, fieldName, fieldName);
		redistributeEntities(newEntity); // Identity changed
		return newEntity;
	}

	// Subclass hooks

	/** Called whenever the set of entities in the file set is changed */
	protected void schemaChanged() {}

	/**
	 * Called whenever a new file is added
	 *
	 * @param file The new file
	 */
	protected void fileAdded(File file) {}

	/**
	 * Called whenever a file is removed
	 *
	 * @param file The deleted file
	 */
	protected void fileRemoved(File file) {}

	/**
	 * Called whenever a file is modified
	 *
	 * @param file The modified file
	 */
	protected void fileChanged(File file) {}

	/**
	 * Called whenever a file is modified
	 *
	 * @param oldFile The file before it was renamed
	 * @param newFile The file after it has been renamed
	 */
	protected void fileRenamed(File oldFile, File newFile) {}

	private void removeFile(File file) throws IOException {
		if (!file.exists()) {
			return;
		}
		if (file.isDirectory()) {
			for (File child : file.listFiles()) {
				removeFile(child);
			}
		}
		if (!file.delete()) {
			throw new IOException("Could not delete entity " + (file.isDirectory() ? "directory" : "file") + " " + file.getPath());
		}
		fileRemoved(file);
	}

	private void renameTypeFile(File oldFile, String oldEntityName, String newEntityName) throws IOException {
		File newFile = new File(oldFile.getParentFile(), oldFile.getName().replace(oldEntityName, newEntityName));
		if (!oldFile.renameTo(newFile)) {
			throw new IOException("Could not rename entity " + (oldFile.isDirectory() ? "directory" : "file") + " " + oldFile.getPath()
			+ " to " + newFile.getPath());
		}
		fileRenamed(oldFile, newFile);
		if (oldFile.isDirectory()) {
			for (File child : oldFile.listFiles()) {
				renameTypeFile(child, oldEntityName, newEntityName);
			}
		}
	}

	private void movedField(EntityFormat oldEntity, EntityFormat newEntity, String oldFieldName, String newFieldName) throws IOException {
		int oldFieldOrder = oldEntity.getFieldOrder().indexOf(oldFieldName);
		int newFieldOrder = newEntity.getFieldOrder().indexOf(newFieldName);
		int minFieldOrder = Math.min(oldFieldOrder, newFieldOrder);
		int maxFieldOrder = Math.max(oldFieldOrder, newFieldOrder);
		schemaChanged();
		File[] entityFiles = getEntityFiles(new File(theDirectory, oldEntity.getName()));
		try (EntityGetterImpl getter = new EntityGetterImpl(oldEntity, entityFiles)) {
			getter.setNewHeader(newEntity.getHeader());
			for (String[] line = getter.getNextLine(true); line != null; line = getter.getNextLine(true)) {
				if (oldFieldOrder == newFieldOrder) {
					continue;
				}
				if (minFieldOrder > 0) {
					System.arraycopy(getter.getLastLine(), 0, getter.getLastLine(), 0, minFieldOrder);
				}
				String movedColumn = line[oldFieldOrder];
				if (oldFieldOrder < newFieldOrder) {
					System.arraycopy(getter.getLastLine(), oldFieldOrder + 1, getter.getLastLine(), oldFieldOrder,
						newFieldOrder - oldFieldOrder);
				} else {
					System.arraycopy(getter.getLastLine(), newFieldOrder, getter.getLastLine(), newFieldOrder + 1,
						oldFieldOrder - newFieldOrder);
				}
				line[newFieldOrder] = movedColumn;
				if (maxFieldOrder < oldEntity.getFieldOrder().size() - 1) {
					System.arraycopy(getter.getLastLine(), maxFieldOrder + 1, getter.getLastLine(), maxFieldOrder + 1,
						oldEntity.getFieldOrder().size());
				}
			}
		} catch (TextParseException e) {
			throw new IllegalStateException("Should not happen", e);
		}
	}

	private static File[] getEntityFiles(File entityDir) {
		File[] files = entityDir.listFiles((dir, name) -> {
			if (!name.startsWith(entityDir.getName())) {
				return false;
			}
			int i;
			for (i = entityDir.getName().length(); i < name.length(); i++) {
				if (name.charAt(i) != ' ') {
					break;
				}
			}
			if (i == name.length()) {
				return false;
			}
			for (; i < name.length(); i++) {
				if (name.charAt(i) < '0' || name.charAt(i) > '9') {
					return false;
				}
			}
			return true;
		});
		Arrays.sort(files, (f1, f2) -> StringUtils.compareNumberTolerant(f1.getName(), f2.getName(), true, true));
		return files;
	}

	private static int getFileIndex(EntityFormat format, QuickMap<String, Object> entityId, int fileCount) {
		int hash = 0;
		for (int f = 0; f < format.getIdFieldCount(); f++) {
			hash = Integer.rotateLeft(hash, 7) ^ Objects.hash(entityId.get(format.getFieldOrder().get(f)));
		}
		int fileIndex = hash % fileCount;
		if (fileIndex < 0) {
			fileIndex += fileCount;
		}
		return fileIndex;
	}

	private int getIdealFileCount(File[] entityFiles) {
		long totalSize = 0;
		for (File f : entityFiles) {
			totalSize += f.length();
		}
		return Math.max(1, (int) Math.round(totalSize * 1.0 / theTargetFileSize));
	}

	/**
	 * <p>
	 * When the set of files among which an entity type's entities are distributed changes (files added or removed), a redistribution must
	 * occur because the file an entity belongs in depends on how many files there are.
	 * </p>
	 * <p>
	 * This method efficiently moves entities to the files they belong in. This method also filters entities with duplicate IDs. This can
	 * happen after a schema change that alters the entity's set of identity fields.
	 * </p>
	 *
	 * @param format The format whose entities to redistribute
	 * @throws IOException If an error occurs rewriting the files
	 */
	private void redistributeEntities(EntityFormat format) throws IOException {
		File[] entityFiles = getEntityFiles(new File(theDirectory, format.getName()));
		EntityGetterImpl[] getters = new EntityGetterImpl[entityFiles.length];
		for (int i = 0; i < entityFiles.length; i++) {
			getters[i] = new EntityGetterImpl(format, new File[] { entityFiles[i] });
		}
		Comparator<QuickMap<String, Object>> idCompare = format::compareIds;
		BetterSortedMap<QuickMap<String, Object>, Integer> sortedGetters = BetterTreeMap.<QuickMap<String, Object>> build(idCompare)
			.safe(false).buildMap();
		List<QuickMap<String, Object>>[] homeless = new List[getters.length];
		for (int i = 0; i < getters.length; i++) {
			QuickMap<String, Object> entity = getters[i].getNextGoodId();
			if (entity != null) {
				while (sortedGetters.putIfAbsent(entity, i) != null) {
					getters[i].delete();
					entity = getters[i].getNextGoodId();
				}
			}
			homeless[i] = new ArrayList<>();
		}
		while (!sortedGetters.isEmpty()) {
			QuickMap<String, Object> entity = sortedGetters.firstKey();
			int g = sortedGetters.values().getFirst();
			sortedGetters.keySet().removeFirst();
			int target = getFileIndex(format, entity, entityFiles.length);
			if (target != g) {
				getters[target].insert(getters[g].getLastLine());
				getters[g].delete();
			}
			entity = getters[g].getNextGoodId();
			if (entity != null) {
				while (sortedGetters.putIfAbsent(entity, g) != null) {
					getters[g].delete();
					entity = getters[g].getNextGoodId();
				}
			}
		}
		for (int i = 0; i < getters.length; i++) {
			try {
				getters[i].close();
			} catch (IOException e) {
				// Need to close all getters
			}
		}
	}

	private EntityFormat parseHeader(String typeName, String[] header, CsvParser parser) throws TextParseException {
		Map<String, TypeToken<?>> fields = new LinkedHashMap<>();
		List<String> ids = new ArrayList<>(header.length);
		for (int f = 0; f < header.length; f++) {
			if (header[f].length() == 0) {
				parser.throwParseException(f, 0, "Empty header column");
			}
			boolean id = header[f].charAt(0) == '*';
			int colonIdx = header[f].indexOf(':');
			String name = header[f].substring(id ? 1 : 0, colonIdx).trim();
			if (id) {
				if (ids.size() != f) {
					parser.throwParseException(f, 0, "ID fields must all be at the beginning of the header");
				}
				ids.add(name);
			} else if (f == 0) {
				parser.throwParseException(f, 0, "First column must be an ID (*)");
			}
			try {
				TypeToken<?> type = TypeTokens.get().parseType(header[f].substring(colonIdx + 1));
				fields.put(name, type);
			} catch (ParseException e) {
				parser.throwParseException(f, colonIdx + 1 + e.getErrorOffset(), e.getMessage(), e);
			}
		}
		return new EntityFormat(typeName, fields, ids);
	}

	class EntityGetterImpl implements EntityIterator {
		private final EntityFormat theFormat;
		private final File[] theFiles;
		private String[] theNewHeader;
		private CharsetEncoder theEncoder;

		private int theFileIndex;
		private Reader theFileReader;
		private RandomAccessFile theFileWriter;
		private CsvParser theFileParser;

		private String[] theLastLine;
		private QuickMap<String, Object> theLastEntity;
		private boolean areNonIdFieldsParsed;

		private int theEntriesParsed;

		EntityGetterImpl(EntityFormat format, File[] files) {
			theFormat = format;
			theFiles = files;
		}

		EntityGetterImpl setNewHeader(String[] header) {
			theNewHeader = header;
			return this;
		}

		@Override
		public EntityFormat getFormat() {
			return theFormat;
		}

		@Override
		public QuickMap<String, Object> getNextId() throws IOException, TextParseException {
			return getNextId(false);
		}

		@Override
		public QuickMap<String, Object> getLastId() {
			return theLastEntity == null ? null : theLastEntity.unmodifiable();
		}

		@Override
		public QuickMap<String, Object> getLast() throws TextParseException {
			if (theLastEntity == null) {
				return null;
			}
			if (!areNonIdFieldsParsed) {
				theFormat.parseNonIds(theLastLine, theLastEntity, theFileParser);
				areNonIdFieldsParsed = true;
			}
			return getLastId();
		}

		@Override
		public void delete() throws IOException {
			if (theLastEntity == null) {
				throw new NoSuchElementException("No entity previously parsed, or entity already deleted");
			}
			write();
			theLastLine = null; // Just prevent the previous line from being written back to the buffer
			theLastEntity = null;
			areNonIdFieldsParsed = false;
		}

		@Override
		public void update(QuickMap<String, Object> entity) throws IOException {
			if (theLastEntity == null) {
				throw new NoSuchElementException("No entity previously parsed, or entity already deleted");
			}
			write();
			for (int f = theFormat.getIdFieldCount(); f < theFormat.getFields().keySize(); f++) {
				int fieldIndex = theFormat.getFields().keyIndex(theFormat.getFieldOrder().get(f));
				if (!Objects.equals(theLastEntity.get(fieldIndex), entity.get(fieldIndex))) {
					theLastLine[f] = ((Format<Object>) theFormat.getFieldFormat(fieldIndex)).format(entity.get(fieldIndex));
					theLastEntity.put(fieldIndex, entity.get(fieldIndex));
				}
			}
			areNonIdFieldsParsed = true;
		}

		QuickMap<String, Object> getNextGoodId() throws IOException {
			try {
				return getNextId(true);
			} catch (TextParseException e) {
				throw new IllegalStateException("Should not happen", e);
			}
		}

		int getEntriesParsed() {
			return theEntriesParsed;
		}

		String[] getLastLine() {
			return theLastLine;
		}

		void insert(QuickMap<String, Object> entity) throws IOException {
			write();
			String[] temp = theLastLine;
			theLastLine = new String[theFormat.getFields().keySize()];
			for (int f = 0; f < theLastLine.length; f++) {
				int fieldIndex = theFormat.getFields().keyIndex(theFormat.getFieldOrder().get(f));
				theLastLine[f] = ((Format<Object>) theFormat.getFieldFormat(fieldIndex)).format(entity.get(fieldIndex));
			}
			flush(false);
			theLastLine = temp;
		}

		void insert(String[] line) throws IOException {
			write();
			String[] temp = theLastLine;
			theLastLine = line;
			flush(false);
			theLastLine = temp;
		}

		void update(String[] line) throws IOException {
			write();
			theLastLine = line;
		}

		private void write() throws IOException {
			if (theFileWriter == null) {
				if (theEncoder == null) {
					theEncoder = Charset.forName("UTF-8").newEncoder();
				}
				theFileWriter = new RandomAccessFile(theFiles[theFileIndex - 1], "rw");
				theFileWriter.seek(theFileParser.getLastLineOffset());
			}
		}

		boolean seek(QuickMap<String, Object> targetEntityId) throws IOException {
			QuickMap<String, Object> next;
			while (true) {
				next = getNextGoodId();// We don't care about parse errors--they may not be for the target entity
				if (next == null) {
					return false;
				}
				int comp = theFormat.compareIds(next, targetEntityId);
				if (comp == 0) {
					return true;
				} else if (comp > 0) {
					return false;
				}
			}
		}

		private QuickMap<String, Object> getNextId(boolean ignoreParseExceptions) throws IOException, TextParseException {
			theLastEntity = null;
			areNonIdFieldsParsed = false;
			String[] line = null;
			QuickMap<String, Object> entity = null;
			QuickMap<String, Object> unmodifiable = null;
			while (true) {
				try {
					line = getNextLine(ignoreParseExceptions);
				} catch (TextParseException e) {
					throw new TextParseException("Bad entry in " + theFormat.getName() + " file " + theFiles[theFileIndex - 1].getName(),
						e);
				} catch (IOException e) {
					theFileParser = null;
					try {
						theFileReader.close();
					} catch (IOException e2) { // Just swallow this one
					} finally {
						theFileReader = null;
					}
					throw new IOException("Read failure in " + theFormat.getName() + " file " + theFiles[theFileIndex - 1].getName(), e);
				}
				if (entity == null) {
					entity = theFormat.getFields().keySet().createMap();
					unmodifiable = entity.unmodifiable();
				}
				if (theFormat.parseIds(line, entity, theFileParser, ignoreParseExceptions)) {
					theEntriesParsed++;
					theLastLine = line;
					theLastEntity = entity;
					return unmodifiable;
				}
			}
		}

		private String[] getNextLine(boolean ignoreParseExceptions) throws IOException, TextParseException {
			while (true) {
				if (theFileParser != null) {
					flush(false);
					try {
						theLastLine = theFileParser.parseNextLine();
					} catch (TextParseException e) {
						if (ignoreParseExceptions) {
							continue;
						}
						try {
							close();
						} catch (IOException e2) { // Just swallow this one
						}
						throw new TextParseException(
							"Bad entry in " + theFormat.getName() + " file " + theFiles[theFileIndex - 1].getName(), e);
					} catch (IOException e) {
						try {
							close();
						} catch (IOException e2) { // Just swallow this one
						}
						throw new IOException("Read failure in " + theFormat.getName() + " file " + theFiles[theFileIndex - 1].getName(),
							e);
					}
					if (theLastLine != null) {
						return theLastLine;
					} else {
						flush(true);
					}
				}
				while (theFileIndex < theFiles.length && theFiles[theFileIndex].isDirectory()) {
					theFileIndex++;
				}
				if (theFileIndex == theFiles.length) {
					return null;
				}
				theFileIndex++;
				try {
					theFileReader = new BufferedReader(new FileReader(theFiles[theFileIndex - 1]));
					theFileParser = new CsvParser(theFileReader, ',');
					theLastLine = theFileParser.parseNextLine();
					if (theLastLine == null) {
						if (ignoreParseExceptions) {
							throw new TextParseException("Empty " + theFormat.getName() + " file " + theFiles[theFileIndex - 1], 0, 0, 0);
						}
					}
					if (theLastLine != null) {
						theFormat.checkHeader(theLastLine, theFiles[theFileIndex - 1], theFileParser);
						if (theNewHeader != null) {
							write();
							theLastLine = theNewHeader;
						}
					}
				} catch (TextParseException e) {
					close();
					if (!ignoreParseExceptions) {
						throw new TextParseException("Bad " + theFormat.getName() + " file " + theFiles[theFileIndex - 1].getName(), e);
					}
				} catch (IOException e) {
					close();
					throw new IOException("Read failure in " + theFormat.getName() + " file " + theFiles[theFileIndex - 1].getName(), e);
				}
				theLastLine = null;
			}
		}

		private void flush(boolean fileEnd) throws IOException {
			if (theFileWriter != null) {
				if (theLastLine != null) {
					for (int i = 0; i < theLastLine.length; i++) {
						if (i > 0) {
							theFileWriter.writeByte(',');
						}
						theEncoder.reset();
						ByteBuffer bytes = theEncoder.encode(CharBuffer.wrap(CsvParser.toCsv(theLastLine[i], ',')));
						while (bytes.hasRemaining()) {
							theFileWriter.writeByte(bytes.get());
						}
					}
					theFileWriter.writeByte('\n');
					theLastLine = null;
				}
				if (fileEnd) {
					int read = theFileReader.read(); // theFileReader is already buffered
					while (read >= 0) {
						theFileWriter.writeByte(read);
						read = theFileReader.read();
					}
					try {
						theFileWriter.close();
					} finally {
						theFileWriter = null;
					}
					fileChanged(theFiles[theFileIndex - 1]);
				}
			}
		}

		@Override
		public void close() throws IOException {
			try {
				flush(true);
			} finally {
				theFileParser = null;
				if (theFileReader != null) {
					try {
						theFileReader.close();
					} finally {
						theFileReader = null;
					}
				}
			}
		}
	}
}
