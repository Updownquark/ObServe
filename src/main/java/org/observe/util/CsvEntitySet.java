package org.observe.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.Charset;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.Function;

import org.qommons.AbstractCharSequence;
import org.qommons.ArrayUtils;
import org.qommons.BiTuple;
import org.qommons.Named;
import org.qommons.QommonsUtils;
import org.qommons.StringUtils;
import org.qommons.collect.QuickSet.QuickMap;
import org.qommons.io.CircularByteBuffer;
import org.qommons.io.CsvParser;
import org.qommons.io.Format;
import org.qommons.io.RandomAccessFileInputStream;
import org.qommons.io.RandomAccessFileOutputStream;
import org.qommons.io.RewritableTextFile;
import org.qommons.io.TextParseException;

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
public class CsvEntitySet implements AutoCloseable {
	/** UTF-8, the char set for the CSV files */
	public static final Charset UTF8 = Charset.forName("UTF-8");

	/**
	 * May be set in any non-ID field of an entity passed to {@link #update(String, QuickMap, boolean)} to preserve the existing value of
	 * that field
	 */
	public static final Object NO_UPDATE = new Object() {
		@Override
		public String toString() {
			return "No Update";
		}
	};

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
		private EntityIndex index;

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
				Class<?> raw = TypeTokens.get().unwrap(TypeTokens.getRawType(fields.get(idField)));
				if (raw.isPrimitive() || raw == String.class) {//
				} else if (raw.isArray()) {
					if (!raw.getComponentType().isPrimitive())
						throw new IllegalArgumentException("ID fields must be primitive, primitive arrays, or Strings");
				} else
					throw new IllegalArgumentException("ID fields must be primitive, primitive arrays, or Strings");
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

		EntityFormat using() throws IOException {
			if (index == null)
				index = new EntityIndex(this, new File(theDirectory, theName + "/" + theName + ".index"));
			return this;
		}

		EntityIndex getIndex() throws IOException {
			return using().index;
		}

		void close() throws IOException {
			if (index != null)
				index.close();
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
			if (index != null) {
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
				int comp = compareIdValue(entity1.get(fieldIndex), entity2.get(fieldIndex));
				if (comp != 0) {
					return comp;
				}
			}
			return 0;
		}

		/**
		 * Creates an entity of this type
		 *
		 * @param forUpdate Whether the entity is to be used for an update operation (non-ID fields will be populated with
		 *        {@link CsvEntitySet#NO_UPDATE}
		 * @return The new entity
		 */
		public QuickMap<String, Object> create(boolean forUpdate) {
			QuickMap<String, Object> entity = theFields.keySet().createMap();
			if (forUpdate) {
				for (int i = theIdFieldCount; i < theFieldOrder.size(); i++)
					entity.put(theFieldOrder.get(i), NO_UPDATE);
			}
			return entity;
		}

		private int compareIdValue(Object id1, Object id2) {
			if (id1 == null || id2 == null)
				throw new IllegalArgumentException("ID fields cannot be null");
			else if (id1 instanceof byte[]) {
				byte[] array1 = (byte[]) id1;
				byte[] array2 = (byte[]) id2;
				for (int i = 0; i < array1.length || i < array2.length; i++) {
					byte b1 = i < array1.length ? array1[i] : 0;
					byte b2 = i < array2.length ? array2[i] : 0;
					int comp = Byte.compare(b1, b2);
					if (comp != 0)
						return comp;
				}
				return 0;
			} else if (id1 instanceof short[]) {
				short[] array1 = (short[]) id1;
				short[] array2 = (short[]) id2;
				for (int i = 0; i < array1.length || i < array2.length; i++) {
					short b1 = i < array1.length ? array1[i] : 0;
					short b2 = i < array2.length ? array2[i] : 0;
					int comp = Short.compare(b1, b2);
					if (comp != 0)
						return comp;
				}
				return 0;
			} else if (id1 instanceof int[]) {
				int[] array1 = (int[]) id1;
				int[] array2 = (int[]) id2;
				for (int i = 0; i < array1.length || i < array2.length; i++) {
					int b1 = i < array1.length ? array1[i] : 0;
					int b2 = i < array2.length ? array2[i] : 0;
					int comp = Integer.compare(b1, b2);
					if (comp != 0)
						return comp;
				}
				return 0;
			} else if (id1 instanceof long[]) {
				long[] array1 = (long[]) id1;
				long[] array2 = (long[]) id2;
				for (int i = 0; i < array1.length || i < array2.length; i++) {
					long b1 = i < array1.length ? array1[i] : 0;
					long b2 = i < array2.length ? array2[i] : 0;
					int comp = Long.compare(b1, b2);
					if (comp != 0)
						return comp;
				}
				return 0;
			} else if (id1 instanceof float[]) {
				float[] array1 = (float[]) id1;
				float[] array2 = (float[]) id2;
				for (int i = 0; i < array1.length || i < array2.length; i++) {
					float b1 = i < array1.length ? array1[i] : 0;
					float b2 = i < array2.length ? array2[i] : 0;
					int comp = Float.compare(b1, b2);
					if (comp != 0)
						return comp;
				}
				return 0;
			} else if (id1 instanceof double[]) {
				double[] array1 = (double[]) id1;
				double[] array2 = (double[]) id2;
				for (int i = 0; i < array1.length || i < array2.length; i++) {
					double b1 = i < array1.length ? array1[i] : 0;
					double b2 = i < array2.length ? array2[i] : 0;
					int comp = Double.compare(b1, b2);
					if (comp != 0)
						return comp;
				}
				return 0;
			} else if (id1 instanceof char[]) {
				char[] array1 = (char[]) id1;
				char[] array2 = (char[]) id2;
				for (int i = 0; i < array1.length || i < array2.length; i++) {
					char b1 = i < array1.length ? array1[i] : 0;
					char b2 = i < array2.length ? array2[i] : 0;
					int comp = Character.compare(b1, b2);
					if (comp != 0)
						return comp;
				}
				return 0;
			} else if (id1 instanceof boolean[]) {
				boolean[] array1 = (boolean[]) id1;
				boolean[] array2 = (boolean[]) id2;
				for (int i = 0; i < array1.length || i < array2.length; i++) {
					boolean b1 = i < array1.length ? array1[i] : false;
					boolean b2 = i < array2.length ? array2[i] : false;
					int comp = Boolean.compare(b1, b2);
					if (comp != 0)
						return comp;
				}
				return 0;
			} else
				return ((Comparable<Object>) id1).compareTo(id2);
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

	@Override
	public void close() throws IOException {
		for (EntityFormat format : theEntityFormats.values())
			format.close();
	}

	/** @return The size to which to grow files in this entity set */
	public long getTargetFileSize() {
		return theTargetFileSize;
	}

	/**
	 * @param targetFileSize The size to which to grow files in this entity set
	 * @return This entity set
	 */
	public CsvEntitySet setTargetFileSize(long targetFileSize) {
		if (targetFileSize < 1024)
			throw new IllegalArgumentException("Target file size must be at least 1024B: " + targetFileSize);
		theTargetFileSize = targetFileSize;
		return this;
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
	 * @return The entity type in this entity set with the given name, or null if no such entity type exists
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
		return new EntityGetterImpl(format.using(), entityFiles, 0);
	}

	/**
	 * @param typeName The name of the entity type to count
	 * @return The number of entities of the given type in this entity set
	 * @throws IOException If the data could not be read
	 * @throws IllegalArgumentException If no such entity type exists in this entity set
	 */
	public long count(String typeName) throws IOException, IllegalArgumentException {
		EntityFormat format = getEntityType(typeName);
		if (format == null) {
			throw new IllegalArgumentException("No such type found: " + typeName);
		}
		return format.getIndex().size();
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
		if (!format.getIndex().seek(entityId))
			return null;
		int fileIndex = format.getIndex().getFileIndex();
		try (EntityGetterImpl getter = new EntityGetterImpl(format, new File[] { entityFiles[fileIndex] }, fileIndex)) {
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
		if (!format.getIndex().seek(entityId))
			return false;
		int fileIndex = format.getIndex().getFileIndex();
		boolean found = false, deleteFile = false;
		try (EntityGetterImpl getter = new EntityGetterImpl(format, new File[] { entityFiles[fileIndex] }, fileIndex)) {
			found = getter.seek(entityId);
			if (found) {
				getter.delete();
				if (getter.getEntriesParsed() == 0 && getIdealFileCount(entityFiles) < entityFiles.length
					&& getter.getNextGoodId() == null) {
					deleteFile = true;
				}
			}
		}
		if (deleteFile) {
			if (!entityFiles[fileIndex].delete()) {
				System.err
				.println(getClass().getSimpleName() + " WARNING: Unable to delete entity file " + entityFiles[fileIndex].getPath());
			}
			fileRemoved(entityFiles[fileIndex]);
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
		if (entityFiles.length > 0 && format.getIndex().seek(entity)) {
			int fileIndex = format.getIndex().getFileIndex();
			File file = entityFiles[fileIndex];
			try (EntityGetterImpl getter = new EntityGetterImpl(format, new File[] { file }, fileIndex)) {
				boolean found = getter.seek(entity);
				if (!found)
					throw new IllegalStateException("Entity in index, but not found");
				getter.update(entity);
				return true;
			}
		}
		if (!insertIfNotFound)
			return false;
		File file;
		int fileIndex;
		if (entityFiles.length < getIdealFileCount(entityFiles)) {
			// Need to add a new file
			file = new File(entityDir, StringUtils.getNewItemName(Arrays.asList(entityFiles),
				f -> f.getName().substring(0, f.getName().length() - 4), typeName, StringUtils.SIMPLE_DUPLICATES) + ".csv");
			try (Writer writer = new FileWriter(file)) {
				format.writeHeader(writer);
			}
			fileAdded(file);
			fileIndex = ArrayUtils.indexOf(getEntityFiles(entityDir), file);
		} else {
			fileIndex = -1;
			file = null;
			long minSize = Long.MAX_VALUE;
			for (int f = 0; f < entityFiles.length; f++) {
				long len = entityFiles[f].length();
				if (len < minSize) {
					fileIndex = f;
					file = entityFiles[f];
					minSize = len;
				}
			}
		}
		try (EntityGetterImpl getter = new EntityGetterImpl(format, new File[] { file }, fileIndex)) {
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
		entity.getIndex().deleteIndex();
		entity.close();
	}

	/**
	 * @param entity The entity type to modify
	 * @param newName The new name for the entity type
	 * @return The altered entity type
	 * @throws IOException If an error occurs moving the data for the entity
	 */
	public EntityFormat renameType(EntityFormat entity, String newName) throws IOException {
		EntityFormat oldEntity = theEntityFormats.get(entity.getName());
		if (oldEntity == null)
			throw new IllegalArgumentException("No such entity type " + entity.getName());
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
		oldEntity.getIndex().deleteIndex();
		oldEntity.close();
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
		BiTuple<QuickMap<String, Object>, String[]> newEntityValue = new BiTuple<>(newEntity.getFields().keySet().createMap(), newLine);
		newEntityValue.getValue1().put(fieldIndex, value);
		reformat(oldEntity, newEntity, (oldEntityValue, oldLine) -> {
			System.arraycopy(oldLine, 0, newLine, 0, fieldOrder);
			if (fieldOrder < oldEntity.getFieldOrder().size()) {
				System.arraycopy(oldLine, fieldOrder, newLine, fieldOrder + 1, oldEntity.getFieldOrder().size() - fieldOrder);
			}
			return newEntityValue;
		}, id, false);
		newEntity.using();
		oldEntity.close();
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
	 * @param value A function to produce initial values for each entity of the given type. If <code>id</code> is true, the function
	 *        <b>must</b> produce the same value when given the same entity multiple times.
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
		BiTuple<QuickMap<String, Object>, String[]> newEntityValue = new BiTuple<>(newEntity.getFields().keySet().createMap(), newLine);
		newEntityValue.getValue1().put(fieldIndex, value);
		reformat(oldEntity, newEntity, (oldEntityValue, oldLine) -> {
			System.arraycopy(oldLine, 0, newLine, 0, fieldOrder);
			F newValue = value.apply(oldEntityValue);
			newLine[fieldOrder] = ((Format<F>) newEntity.getFieldFormat(fieldIndex)).format(newValue);
			if (fieldOrder < oldEntity.getFieldOrder().size()) {
				System.arraycopy(oldLine, fieldOrder, newLine, fieldOrder + 1, oldEntity.getFieldOrder().size() - fieldOrder);
			}
			return newEntityValue;
		}, id, true);
		newEntity.using();
		oldEntity.close();
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
		BiTuple<QuickMap<String, Object>, String[]> newEntityValue = new BiTuple<>(newEntity.getFields().keySet().createMap(), newLine);
		reformat(oldEntity, newEntity, (oldEntityValue, oldLine) -> {
			if (fieldOrder > 0) {
				System.arraycopy(oldLine, 0, newLine, 0, fieldOrder);
			}
			if (fieldOrder < oldEntity.getFieldOrder().size()) {
				System.arraycopy(oldLine, fieldOrder + 1, newLine, fieldOrder, oldEntity.getFieldOrder().size() - fieldOrder - 1);
			}
			return newEntityValue;
		}, fieldOrder < oldEntity.getIdFieldCount(), false);
		newEntity.using();
		oldEntity.close();
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
		int oldFieldOrder = oldEntity.getFieldOrder().indexOf(oldFieldName);
		int newFieldOrder = newEntity.getFieldOrder().indexOf(newFieldName);
		movedField(oldEntity, newEntity, oldFieldName, newFieldName,
			oldFieldOrder != newFieldOrder && oldFieldOrder < oldEntity.getIdFieldCount());
		oldEntity.close();
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
		movedField(oldEntity, newEntity, fieldName, fieldName, true);
		oldEntity.close();
		return newEntity;
	}

	private void movedField(EntityFormat oldEntity, EntityFormat newEntity, String oldFieldName, String newFieldName, boolean idChange)
		throws IOException {
		int oldFieldOrder = oldEntity.getFieldOrder().indexOf(oldFieldName);
		int newFieldOrder = newEntity.getFieldOrder().indexOf(newFieldName);
		int minFieldOrder = Math.min(oldFieldOrder, newFieldOrder);
		int maxFieldOrder = Math.max(oldFieldOrder, newFieldOrder);
		schemaChanged();
		int oldFieldIndex = oldEntity.getFields().keyIndex(oldFieldName);
		int newFieldIndex = newEntity.getFields().keyIndex(newFieldName);
		QuickMap<String, Object> newEntityValue = newEntity.getFields().keySet().createMap();
		reformat(oldEntity, newEntity, (oldEntityValue, oldLine) -> {
			newEntityValue.put(newFieldIndex, oldEntityValue.get(oldFieldIndex));
			if (oldFieldOrder != newFieldOrder) {
				if (minFieldOrder > 0) {
					System.arraycopy(oldLine, 0, oldLine, 0, minFieldOrder);
				}
				String movedColumn = oldLine[oldFieldOrder];
				if (oldFieldOrder < newFieldOrder) {
					System.arraycopy(oldLine, oldFieldOrder + 1, oldLine, oldFieldOrder, newFieldOrder - oldFieldOrder);
				} else {
					System.arraycopy(oldLine, newFieldOrder, oldLine, newFieldOrder + 1, oldFieldOrder - newFieldOrder);
				}
				oldLine[newFieldOrder] = movedColumn;
				if (maxFieldOrder < oldEntity.getFieldOrder().size() - 1) {
					System.arraycopy(oldLine, maxFieldOrder + 1, oldLine, maxFieldOrder + 1, oldEntity.getFieldOrder().size());
				}
			}
			return new BiTuple<>(newEntityValue, oldLine);
		}, idChange, false);
		newEntity.using();
	}

	// Subclass hooks

	/** Called whenever the set of entities in the file set is changed */
	protected void schemaChanged() {
	}

	/**
	 * Called whenever a new file is added
	 *
	 * @param file The new file
	 * @throws IOException If an error occurs handling the file addition
	 */
	protected void fileAdded(File file) throws IOException {
	}

	/**
	 * Called whenever a file is removed
	 *
	 * @param file The deleted file
	 * @throws IOException If an error occurs handling the file deletion
	 */
	protected void fileRemoved(File file) throws IOException {
	}

	/**
	 * Called whenever a file is modified
	 *
	 * @param file The modified file
	 * @throws IOException If an error occurs handling the file update
	 */
	protected void fileChanged(File file) throws IOException {
	}

	/**
	 * Called whenever a file is modified
	 *
	 * @param oldFile The file before it was renamed
	 * @param newFile The file after it has been renamed
	 * @throws IOException If an error occurs handling the file rename
	 */
	protected void fileRenamed(File oldFile, File newFile) throws IOException {
	}

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
		if (oldFile.isDirectory()) {
			for (File child : oldFile.listFiles()) {
				renameTypeFile(child, oldEntityName, newEntityName);
			}
		}
		fileRenamed(oldFile, newFile);
	}

	private static File[] getEntityFiles(File entityDir) {
		File[] files = entityDir.listFiles((dir, name) -> {
			if (!name.startsWith(entityDir.getName()) || !name.endsWith(".csv")) {
				return false;
			}
			int i;
			for (i = entityDir.getName().length(); i < name.length() - 4; i++) {
				if (name.charAt(i) != ' ') {
					break;
				}
			}
			if (i == name.length()) {
				return false;
			}
			for (; i < name.length() - 4; i++) {
				if (name.charAt(i) < '0' || name.charAt(i) > '9') {
					return false;
				}
			}
			return true;
		});
		Arrays.sort(files, (f1, f2) -> StringUtils.compareNumberTolerant(//
			f1.getName().substring(0, f1.getName().length() - 4), //
			f2.getName().substring(0, f2.getName().length() - 4), true, true));
		return files;
	}

	private int getIdealFileCount(File[] entityFiles) {
		long totalSize = 0;
		for (File f : entityFiles) {
			totalSize += f.length();
		}
		return Math.max(1, (int) Math.round(totalSize * 1.0 / theTargetFileSize));
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

	interface Reformatter {
		BiTuple<QuickMap<String, Object>, String[]> reformat(QuickMap<String, Object> identity, String[] line);
	}

	private void reformat(EntityFormat oldFormat, EntityFormat newFormat, Reformatter reformatter, boolean idChange, boolean requestNonIds)
		throws IOException {
		File[] entityFiles = getEntityFiles(new File(theDirectory, oldFormat.getName()));
		oldFormat.getIndex().goToBeginning();
		File tempIndexFile = idChange ? File.createTempFile(newFormat.getName(), "index") : null;
		EntityIndex newIndex = idChange ? new EntityIndex(newFormat, tempIndexFile) : null;
		newFormat.getIndex().goToBeginning();
		EntityGetterImpl[] getters = new EntityGetterImpl[entityFiles.length];
		for (int i = 0; i < entityFiles.length; i++) {
			getters[i] = new EntityGetterImpl(oldFormat, new File[] { entityFiles[i] }, i);
			getters[i].setNewHeader(newFormat.getHeader());
		}
		int[] newFieldIndexes = new int[newFormat.getFields().keySize()];
		int[] oldFieldIndexes = new int[newFormat.getFields().keySize()];
		int[] oldFieldOrders = new int[newFormat.getFields().keySize()];
		for (int f = 0; f < newFieldIndexes.length; f++) {
			String fieldName = newFormat.getFieldOrder().get(f);
			newFieldIndexes[f] = newFormat.getFields().keyIndex(fieldName);
			oldFieldIndexes[f] = oldFormat.getFields().keyIndexTolerant(fieldName);
			oldFieldOrders[f] = oldFieldIndexes[f] < 0 ? -1 : oldFormat.getFieldOrder().indexOf(fieldName);
		}
		while (!oldFormat.getIndex().isAtEnd()) {
			oldFormat.getIndex().readNext();
			int file = oldFormat.getIndex().getFileIndex();
			QuickMap<String, Object> oldEntity = getters[file].getNextGoodId();
			if (requestNonIds) {
				try {
					getters[file].getLast();
				} catch (TextParseException e) {
				}
			}
			BiTuple<QuickMap<String, Object>, String[]> newEntity = reformatter.reformat(oldEntity, getters[file].getLastLine());
			for (int f = 0; f < newFieldIndexes.length; f++) {
				if (f < newFormat.getIdFieldCount() && oldFieldIndexes[f] >= 0)
					newEntity.getValue1().put(newFieldIndexes[f], oldEntity.get(oldFieldIndexes[f]));
			}
			if (idChange)
				newIndex.insert(newEntity.getValue1(), file);
			getters[file].update(newEntity.getValue2());
		}
		for (int i = 0; i < getters.length; i++)
			getters[i].close();
		if (tempIndexFile != null) {
			tempIndexFile.renameTo(new File(theDirectory, newFormat.getName() + "/" + newFormat.getName() + ".index"));
			tempIndexFile.delete();
		}
	}

	/**
	 * @param format The entity format to iterate through entities for
	 * @param file The entity file to iterate through
	 * @return An entity getter that allows subclasses to iterate through all entities in a specific file
	 */
	protected EntityGetterImpl iterate(EntityFormat format, File file) {
		return new EntityGetterImpl(format, new File[] { file }, //
			ArrayUtils.indexOf(getEntityFiles(new File(theDirectory, format.getName())), file));
	}

	/**
	 * Parses identity fields from a CSV line into an entity
	 *
	 * @param format The entity format to parse information for
	 * @param line The CSV line to parse
	 * @param entity The entity to parse data into
	 * @param fileParser The CSV parser that parsed the line
	 * @param ignoreParseExceptions Whether to ignore exceptions parsing the fields and return false
	 * @return Whether all fields were successfully parsed
	 * @throws TextParseException If an error occurs parsing a field
	 */
	protected boolean parseIds(EntityFormat format, String[] line, QuickMap<String, Object> entity, CsvParser fileParser,
		boolean ignoreParseExceptions) throws TextParseException {
		for (int f = 0; f < format.getIdFieldCount(); f++) {
			int fieldIndex = format.getFields().keyIndex(format.getFieldOrder().get(f));
			try {
				entity.put(fieldIndex, format.getFieldFormat(fieldIndex).parse(line[f]));
			} catch (ParseException e) {
				if (ignoreParseExceptions) {
					return false;
				}
				fileParser.throwParseException(f, e.getErrorOffset(), e.getMessage(), e);
			}
		}
		return true;
	}

	/**
	 * Parses non-identity fields from a CSV line into an entity
	 *
	 * @param format The entity format to parse information for
	 * @param line The CSV line to parse
	 * @param entity The entity to parse data into
	 * @param fileParser The CSV parser that parsed the line
	 * @throws TextParseException If an error occurs parsing a field
	 */
	protected void parseNonIds(EntityFormat format, String[] line, QuickMap<String, Object> entity, CsvParser fileParser)
		throws TextParseException {
		for (int f = format.getIdFieldCount(); f < format.getFields().keySize(); f++) {
			int fieldIndex = format.getFields().keyIndex(format.getFieldOrder().get(f));
			try {
				entity.put(fieldIndex, format.getFieldFormat(fieldIndex).parse(line[f]));
			} catch (ParseException e) {
				fileParser.throwParseException(f, e.getErrorOffset(), e.getMessage(), e);
			}
		}
	}

	interface ByteConsumer {
		void next(byte b) throws IOException;
	}

	interface ByteSupplier {
		byte next() throws IOException;

		void skip(int count) throws IOException;
	}

	interface ByteCompare {
		int compare(ByteSupplier b1, ByteSupplier b2) throws IOException;
	}

	interface ByteSerializer {
		void serialize(Object value, ByteConsumer bytes) throws IOException;
	}

	static class SimpleByteCompare implements ByteCompare {
		final int size;
		final boolean firstSigned;

		SimpleByteCompare(int size, boolean firstSigned) {
			this.size = size;
			this.firstSigned = firstSigned;
		}

		@Override
		public int compare(ByteSupplier b1, ByteSupplier b2) throws IOException {
			boolean first = true;
			int i = 0;
			try {
				for (; i < size; i++) {
					int comp;
					if (first && firstSigned) {
						first = false;
						comp = Byte.compare(b1.next(), b2.next());
					} else
						comp = Integer.compare((b1.next() & 0xff), (b2.next() & 0xff));
					if (comp != 0)
						return comp;
				}
			} finally {
				if (i < size) {
					b1.skip(size - i);
					b2.skip(size - i);
				}
			}
			return 0;
		}
	}

	static class FloatByteCompare implements ByteCompare {
		@Override
		public int compare(ByteSupplier b1, ByteSupplier b2) throws IOException {
			int f1 = 0, f2 = 0;
			for (int i = 0; i < 4; i++) {
				f1 = (f1 << 8) | (b1.next() & 0xff);
				f2 = (f2 << 8) | (b2.next() & 0xff);
			}
			return Float.compare(Float.intBitsToFloat(f1), Float.intBitsToFloat(f2));
		}
	}

	static class DoubleByteCompare implements ByteCompare {
		@Override
		public int compare(ByteSupplier b1, ByteSupplier b2) throws IOException {
			long d1 = 0, d2 = 0;
			for (int i = 0; i < 8; i++) {
				d1 = (d1 << 8) | (b1.next() & 0xff);
				d2 = (d2 << 8) | (b2.next() & 0xff);
			}
			return QommonsUtils.compareDoubleBits(d1, d2);
		}
	}

	static class ArrayByteCompare implements ByteCompare {
		private final int length;
		private final int elementSize;
		private final ByteCompare elementCompare;

		ArrayByteCompare(int length, int elementSize, ByteCompare elementCompare) {
			this.length = length;
			this.elementSize = elementSize;
			this.elementCompare = elementCompare;
		}

		@Override
		public int compare(ByteSupplier b1, ByteSupplier b2) throws IOException {
			int i = 0;
			try {
				for (; i < length; i++) {
					int comp = elementCompare.compare(b1, b2);
					if (comp != 0)
						return comp;
				}
			} finally {
				if (i < length) {
					b1.skip(elementSize * (length - i));
					b2.skip(elementSize * (length - i));
				}
			}
			return 0;
		}
	}

	static class IdSerializer {
		final int size;
		final int[] idFields;
		final ByteSerializer[] serializer;
		final ByteCompare[] compares;

		IdSerializer(EntityFormat entity) {
			int sz = 0;
			idFields = new int[entity.getIdFieldCount()];
			serializer = new ByteSerializer[entity.getIdFieldCount()];
			compares = new ByteCompare[entity.getIdFieldCount()];
			for (int i = 0; i < serializer.length; i++) {
				idFields[i] = entity.getFields().keyIndex(entity.getFieldOrder().get(i));
				Class<?> type = TypeTokens.get().unwrap(TypeTokens.getRawType(entity.getFields().get(idFields[i])));
				if (type == byte.class) {
					sz++;
					serializer[i] = (b, c) -> c.next(((Byte) b).byteValue());
					compares[i] = new SimpleByteCompare(1, true);
				} else if (type == short.class) {
					sz += 2;
					serializer[i] = (b, c) -> {
						short s = ((Short) b).shortValue();
						c.next((byte) (s >>> 8));
						c.next((byte) s);
					};
					compares[i] = new SimpleByteCompare(2, true);
				} else if (type == int.class) {
					sz += 4;
					serializer[i] = (b, c) -> {
						int s = ((Integer) b).intValue();
						c.next((byte) (s >>> 24));
						c.next((byte) (s >>> 16));
						c.next((byte) (s >>> 8));
						c.next((byte) s);
					};
					compares[i] = new SimpleByteCompare(4, true);
				} else if (type == long.class) {
					sz += 8;
					serializer[i] = (b, c) -> {
						long s = ((Long) b).longValue();
						c.next((byte) (s >>> 56));
						c.next((byte) (s >>> 48));
						c.next((byte) (s >>> 40));
						c.next((byte) (s >>> 32));
						c.next((byte) (s >>> 24));
						c.next((byte) (s >>> 16));
						c.next((byte) (s >>> 8));
						c.next((byte) s);
					};
					compares[i] = new SimpleByteCompare(8, true);
				} else if (type == float.class) {
					sz += 4;
					serializer[i] = (b, c) -> {
						int s = Float.floatToIntBits(((Float) b).floatValue());
						c.next((byte) (s >>> 24));
						c.next((byte) (s >>> 16));
						c.next((byte) (s >>> 8));
						c.next((byte) s);
					};
					compares[i] = new FloatByteCompare();
				} else if (type == double.class) {
					sz += 8;
					serializer[i] = (b, c) -> {
						long s = Double.doubleToLongBits(((Double) b).doubleValue());
						c.next((byte) (s >>> 56));
						c.next((byte) (s >>> 48));
						c.next((byte) (s >>> 40));
						c.next((byte) (s >>> 32));
						c.next((byte) (s >>> 24));
						c.next((byte) (s >>> 16));
						c.next((byte) (s >>> 8));
						c.next((byte) s);
					};
					compares[i] = new DoubleByteCompare();
				} else if (type == char.class) {
					sz += 2;
					serializer[i] = (b, c) -> {
						char s = ((Character) b).charValue();
						c.next((byte) (s >>> 8));
						c.next((byte) s);
					};
					compares[i] = new SimpleByteCompare(2, false);
				} else if (type == boolean.class) {
					sz++;
					serializer[i] = (b, c) -> {
						c.next(((Boolean) b).booleanValue() ? (byte) 1 : (byte) 0);
					};
					compares[i] = new SimpleByteCompare(1, false);
				} else if (type == String.class) {
					sz += 512;
					serializer[i] = (b, c) -> {
						String s = (String) b;
						if (s.length() > 256)
							throw new IllegalArgumentException("Identity strings cannot be longer than 256 characters");
						int ch;
						for (ch = 0; ch < 256; ch++) {
							if (ch < s.length()) {
								c.next((byte) (s.charAt(ch) >>> 8));
								c.next((byte) s.charAt(ch));
							} else {
								c.next((byte) 0);
								c.next((byte) 0);
							}
						}
					};
					compares[i] = new SimpleByteCompare(512, false);
				} else if (type == byte[].class) {
					sz += 8;
					serializer[i] = (b, c) -> {
						byte[] array = (byte[]) b;
						for (int j = 0; j < 8; j++) {
							if (j < array.length)
								c.next(array[j]);
							else
								c.next((byte) 0);
						}
					};
					compares[i] = new ArrayByteCompare(8, 1, new SimpleByteCompare(1, true));
				} else if (type == short[].class) {
					sz += 16;
					serializer[i] = (b, c) -> {
						short[] array = (short[]) b;
						for (int j = 0; j < 8; j++) {
							if (j < array.length) {
								c.next((byte) (array[j] >>> 8));
								c.next((byte) array[j]);
							} else {
								c.next((byte) 0);
								c.next((byte) 0);
							}
						}
					};
					compares[i] = new ArrayByteCompare(8, 2, new SimpleByteCompare(2, true));
				} else if (type == int[].class) {
					sz += 32;
					serializer[i] = (b, c) -> {
						int[] array = (int[]) b;
						for (int j = 0; j < 8; j++) {
							if (j < array.length) {
								c.next((byte) (array[j] >>> 24));
								c.next((byte) (array[j] >>> 16));
								c.next((byte) (array[j] >>> 8));
								c.next((byte) array[j]);
							} else {
								c.next((byte) 0);
								c.next((byte) 0);
								c.next((byte) 0);
								c.next((byte) 0);
							}
						}
					};
					compares[i] = new ArrayByteCompare(8, 4, new SimpleByteCompare(4, true));
				} else if (type == long[].class) {
					sz += 64;
					serializer[i] = (b, c) -> {
						long[] array = (long[]) b;
						for (int j = 0; j < 8; j++) {
							if (j < array.length) {
								c.next((byte) (array[j] >>> 56));
								c.next((byte) (array[j] >>> 48));
								c.next((byte) (array[j] >>> 40));
								c.next((byte) (array[j] >>> 32));
								c.next((byte) (array[j] >>> 24));
								c.next((byte) (array[j] >>> 16));
								c.next((byte) (array[j] >>> 8));
								c.next((byte) array[j]);
							} else {
								c.next((byte) 0);
								c.next((byte) 0);
								c.next((byte) 0);
								c.next((byte) 0);
								c.next((byte) 0);
								c.next((byte) 0);
								c.next((byte) 0);
								c.next((byte) 0);
							}
						}
					};
					compares[i] = new ArrayByteCompare(8, 8, new SimpleByteCompare(8, true));
				} else if (type == float[].class) {
					sz += 32;
					serializer[i] = (b, c) -> {
						float[] array = (float[]) b;
						for (int j = 0; j < 8; j++) {
							if (j < array.length) {
								int bits = Float.floatToIntBits(array[j]);
								c.next((byte) (bits >>> 24));
								c.next((byte) (bits >>> 16));
								c.next((byte) (bits >>> 8));
								c.next((byte) bits);
							} else {
								c.next((byte) 0);
								c.next((byte) 0);
								c.next((byte) 0);
								c.next((byte) 0);
							}
						}
					};
					compares[i] = new ArrayByteCompare(8, 4, new FloatByteCompare());
				} else if (type == double[].class) {
					sz += 64;
					serializer[i] = (b, c) -> {
						double[] array = (double[]) b;
						for (int j = 0; j < 8; j++) {
							if (j < array.length) {
								long bits = Double.doubleToLongBits(array[j]);
								c.next((byte) (bits >>> 56));
								c.next((byte) (bits >>> 48));
								c.next((byte) (bits >>> 40));
								c.next((byte) (bits >>> 32));
								c.next((byte) (bits >>> 24));
								c.next((byte) (bits >>> 16));
								c.next((byte) (bits >>> 8));
								c.next((byte) bits);
							} else {
								c.next((byte) 0);
								c.next((byte) 0);
								c.next((byte) 0);
								c.next((byte) 0);
								c.next((byte) 0);
								c.next((byte) 0);
								c.next((byte) 0);
								c.next((byte) 0);
							}
						}
					};
					compares[i] = new ArrayByteCompare(8, 8, new DoubleByteCompare());
				} else if (type == char[].class) {
					sz += 16;
					serializer[i] = (b, c) -> {
						char[] array = (char[]) b;
						for (int j = 0; j < 8; j++) {
							if (j < array.length) {
								c.next((byte) (array[j] >>> 8));
								c.next((byte) array[j]);
							} else {
								c.next((byte) 0);
								c.next((byte) 0);
							}
						}
					};
					compares[i] = new ArrayByteCompare(8, 2, new SimpleByteCompare(2, true));
				} else if (type == boolean[].class) {
					sz += 8;
					serializer[i] = (b, c) -> {
						boolean[] array = (boolean[]) b;
						for (int j = 0; j < 8; j++) {
							if (j < array.length)
								c.next(array[j] ? (byte) 1 : (byte) 0);
							else
								c.next((byte) 0);
						}
					};
					compares[i] = new ArrayByteCompare(8, 1, new SimpleByteCompare(1, true));
				} else
					throw new IllegalStateException("Unhandled ID type: " + type.getName());
			}
			size = sz;
		}

		void serialize(QuickMap<String, Object> id, ByteConsumer b) throws IOException {
			for (int i = 0; i < serializer.length; i++)
				serializer[i].serialize(id.get(idFields[i]), b);
		}

		int compare(ByteSupplier b1, ByteSupplier b2) throws IOException {
			for (ByteCompare c : compares) {
				int comp = c.compare(b1, b2);
				if (comp != 0)
					return comp;
			}
			return 0;
		}
	}

	private static class ByteArrayIO implements ByteConsumer, ByteSupplier {
		final byte[] bytes;
		private int position;

		ByteArrayIO(int length) {
			bytes = new byte[length];
		}

		@Override
		public void next(byte b) throws IOException {
			bytes[position++] = b;
		}

		@Override
		public byte next() throws IOException {
			return bytes[position++];
		}

		@Override
		public void skip(int count) throws IOException {
			position += count;
		}

		ByteArrayIO reset() {
			position = 0;
			return this;
		}

		@Override
		public String toString() {
			StringBuilder str = new StringBuilder();
			for (int i = 0; i < bytes.length; i++) {
				if (i > 0)
					str.append(',');
				if (position == i)
					str.append('^');
				str.append(bytes[i]);
			}
			if (position == bytes.length)
				str.append('^');
			return str.toString();
		}
	}

	class EntityIndex {
		private final File theIndexFile;
		private final RandomAccessFile theFile;
		private final IdSerializer theSerializer;
		private final int theEntrySize;

		private final ByteArrayIO theLastEntry;
		private final ByteArrayIO theSeekEntry;
		private final byte[] theLastSeek;
		private int theFileIndex;

		EntityIndex(EntityFormat entity, File indexFile) throws IOException {
			theIndexFile = indexFile;
			theFile = new RandomAccessFile(indexFile, "rw");
			theSerializer = new IdSerializer(entity);
			theLastEntry = new ByteArrayIO(theSerializer.size);
			theSeekEntry = new ByteArrayIO(theSerializer.size);
			theLastSeek = new byte[theSerializer.size];
			theEntrySize = theSerializer.size + 4;

			reset();
		}

		private void reset() throws IOException {
			long totalEntries = theFile.length() / theEntrySize;
			if (totalEntries > 1)
				theFile.seek(totalEntries / 2 * theEntrySize);
			if (totalEntries > 0) {
				readNext();
				System.arraycopy(theLastEntry.bytes, 0, theLastSeek, 0, theLastSeek.length);
			}
		}

		long size() throws IOException {
			return theFile.length() / theEntrySize;
		}

		void close() throws IOException {
			theFile.close();
		}

		boolean seek(QuickMap<String, Object> entityId) throws IOException {
			long totalEntries = theFile.length() / theEntrySize;
			if (setSeek(entityId))
				return totalEntries > 0 && Arrays.equals(theLastEntry.bytes, theSeekEntry.bytes);
				if (totalEntries == 0)
					return false;
				long min = 0, max = totalEntries - 1;
				long position = theFile.getFilePointer() / theEntrySize - 1;
				while (min <= max) {
					int comp = theSerializer.compare(theSeekEntry.reset(), theLastEntry.reset());
					if (comp == 0)
						return true;
					else if (comp < 0)
						max = position - 1;
					else
						min = position + 1;
					if (max < min)
						break;
					position = (min + max) / 2;
					long seek = position * theEntrySize;
					if (theFile.getFilePointer() != seek)
						theFile.seek(seek);
					readNext();
				}
				return false;
		}

		int getFileIndex() {
			return theFileIndex;
		}

		void insert(QuickMap<String, Object> entityId, int fileIndex) throws IOException {
			setSeek(entityId);
			insert(fileIndex);
		}

		void insert(int fileIndex) throws IOException {
			theFileIndex = fileIndex;
			if (theFile.length() == 0) {
				theFile.write(theSeekEntry.bytes);
				theFile.writeInt(fileIndex);
			} else {
				int comp = theSerializer.compare(theLastEntry.reset(), theSeekEntry.reset());
				if (comp > 0)
					theFile.seek(theFile.getFilePointer() - theEntrySize);
				if (theFile.getFilePointer() == theFile.length()) {
					theFile.write(theSeekEntry.bytes);
					theFile.writeInt(fileIndex);
				} else {
					CircularByteBuffer buffer = new CircularByteBuffer(64 * 1024);
					InputStream in;
					long inPos;
					if (theFile.getFilePointer() > 1024) {
						RandomAccessFile raf = new RandomAccessFile(theIndexFile, "r");
						raf.seek(theFile.getFilePointer());
						in = new RandomAccessFileInputStream(raf);
						inPos = theFile.getFilePointer();
					} else {
						in = new FileInputStream(theIndexFile);
						inPos = 0;
						if (theFile.getFilePointer() > 0) {
							inPos = in.skip(theFile.getFilePointer());
							while (inPos < theFile.getFilePointer()) {
								inPos += in.skip(theFile.getFilePointer() - inPos);
							}
						}
					}
					try {
						int read = buffer.appendFrom(in, buffer.getCapacity());
						int totalRead = read;
						inPos += read;
						while (read >= 0 && totalRead < theEntrySize) {
							read = buffer.appendFrom(in, buffer.getCapacity());
							if (read >= 0) {
								totalRead += read;
								inPos += read;
							}
						}
						if (totalRead < 0)
							throw new IOException("Unexpected end of file");
						theFile.write(theSeekEntry.bytes);
						theFile.writeInt(fileIndex);
						long fp = theFile.getFilePointer();
						RandomAccessFileOutputStream out = new RandomAccessFileOutputStream(theFile);
						while (true) {
							int write = (int) (inPos - theFile.getFilePointer());
							buffer.writeContent(out, 0, write).delete(0, write);
							read = buffer.appendFrom(in, buffer.getCapacity() - buffer.length());
							if (read >= 0)
								inPos += read;
							else
								break;
						}
						buffer.writeContent(out, 0, buffer.length());
						theFile.seek(fp);
					} finally {
						in.close();
					}
				}
			}
			System.arraycopy(theSeekEntry.bytes, 0, theLastEntry.bytes, 0, theLastSeek.length);
		}

		void deleteIndex() throws IOException {
		}

		void delete() throws IOException {
			long newLen = theFile.length() - theEntrySize;
			if (theFile.getFilePointer() < theFile.length()) {
				byte[] buffer = new byte[64 * 1024];
				try (InputStream in = new FileInputStream(theIndexFile)) {
					long skipped = in.skip(theFile.getFilePointer());
					while (skipped < theFile.getFilePointer()) {
						skipped += in.skip(theFile.getFilePointer() - skipped);
					}
					theFile.seek(theFile.getFilePointer() - theEntrySize);
					int read = in.read(buffer, 0, buffer.length);
					while (read >= 0) {
						if (read > 0)
							theFile.write(buffer, 0, read);
						read = in.read(buffer, 0, buffer.length);
					}
				}
			}
			theFile.setLength(newLen);
			reset();
		}

		void readNext() throws IOException {
			int read = theFile.read(theLastEntry.bytes);
			int totalRead = read;
			while (read >= 0 && read < theLastEntry.bytes.length) {
				read = theFile.read(theLastEntry.bytes, totalRead, theLastEntry.bytes.length - totalRead);
				totalRead += read;
			}
			if (read < 0)
				throw new IOException("Unexpected end of file");
			theFileIndex = theFile.readInt();
		}

		void goToBeginning() throws IOException {
			theFile.seek(0);
		}

		boolean isAtEnd() throws IOException {
			return theFile.getFilePointer() == theFile.length();
		}

		private boolean setSeek(QuickMap<String, Object> entityId) throws IOException {
			theSerializer.serialize(entityId, theSeekEntry.reset());
			if (theFile.getFilePointer() == 0)
				return false;
			else if (Arrays.equals(theSeekEntry.bytes, theLastSeek))
				return true;
			System.arraycopy(theSeekEntry.bytes, 0, theLastSeek, 0, theLastSeek.length);
			return false;
		}
	}

	/** {@link EntityIterator} implementation that allows a little lower-level capability */
	protected class EntityGetterImpl implements EntityIterator {
		private final EntityFormat theFormat;
		private final File[] theFiles;
		private final int theFileIndexOffset;
		private String[] theNewHeader;

		private int theFileIndex;
		private RewritableTextFile theFileData;
		private Reader theFileReader;
		private Writer theFileWriter;
		private CsvParser theFileParser;

		private long theLastLineOffset;
		private String[] theLastLine;
		private QuickMap<String, Object> theLastEntity;
		private boolean areNonIdFieldsParsed;

		private int theEntriesParsed;

		EntityGetterImpl(EntityFormat format, File[] files, int fileIndexOffset) {
			theFormat = format;
			theFiles = files;
			theFileIndexOffset = fileIndexOffset;
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
				parseNonIds(theFormat, theLastLine, theLastEntity, theFileParser);
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
			theFormat.getIndex().delete();
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
				Object fieldValue = entity.get(fieldIndex);
				if (fieldValue != NO_UPDATE && !Objects.equals(theLastEntity.get(fieldIndex), fieldValue)) {
					theLastLine[f] = ((Format<Object>) theFormat.getFieldFormat(fieldIndex)).format(fieldValue);
					theLastEntity.put(fieldIndex, fieldValue);
				}
			}
			areNonIdFieldsParsed = true;
		}

		/**
		 * @return The next ID that could actually be parsed
		 * @throws IOException If an error occurs reading the file
		 */
		public QuickMap<String, Object> getNextGoodId() throws IOException {
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
			theFormat.getIndex().seek(entity);
			theFormat.getIndex().insert(theFileIndexOffset + theFileIndex - 1);
			flush();
			theLastLine = temp;
		}

		void insert(String[] line) throws IOException {
			write();
			String[] temp = theLastLine;
			theLastLine = line;
			flush();
			theLastLine = temp;
		}

		void update(String[] line) throws IOException {
			write();
			theLastLine = line;
		}

		private void write() throws IOException {
			if (theFileWriter == null)
				theFileWriter = theFileData.getOut(theLastLineOffset);
		}

		boolean seek(Comparable<QuickMap<String, Object>> entityCompare) throws IOException {
			QuickMap<String, Object> next;
			while (true) {
				next = getNextGoodId();// We don't care about parse errors--they may not be for the target entity
				if (next == null) {
					return false;
				}
				int comp = entityCompare.compareTo(next);
				if (comp == 0) {
					return true;
				} else if (comp > 0) {
					return false;
				}
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
						theFileData.close();
					} catch (IOException e2) { // Just swallow this one
					} finally {
						theFileReader = null;
						theFileData = null;
					}
					throw new IOException("Read failure in " + theFormat.getName() + " file " + theFiles[theFileIndex - 1].getName(), e);
				}
				if (line == null)
					return null;
				if (entity == null) {
					entity = theFormat.getFields().keySet().createMap();
					unmodifiable = entity.unmodifiable();
				}
				if (parseIds(theFormat, line, entity, theFileParser, ignoreParseExceptions)) {
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
					flush();
					try {
						theLastLineOffset = theFileData.getInputPosition();
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
						if (theLastLine.length != theFormat.getFields().keySize())
							theFileParser.throwParseException(0, 0,
								"Expected " + theFormat.getFields().keySize() + " columns but found " + theLastLine.length);
						for (int i = 0; i < theLastLine.length; i++)
							theLastLine[i] = unescapeCsvEntry(theLastLine[i], theFileParser, i);
						return theLastLine;
					} else {
						flush();
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
					theFileData = new RewritableTextFile(theFiles[theFileIndex - 1], UTF8, -1);
					theFileReader = theFileData.getIn();
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

		private void flush() throws IOException {
			if (theFileWriter != null && theLastLine != null) {
				for (int i = 0; i < theLastLine.length; i++) {
					if (i > 0) {
						theFileWriter.write(',');
					}
					theFileWriter.write(escapeCsvEntry(theLastLine[i]).toString());
				}
				theFileWriter.write('\n');
				theFileWriter.flush();
				theLastLine = null;
			}
		}

		@Override
		public void close() throws IOException {
			theFileParser = null;
			try {
				flush();
			} finally {
				theFileData.transfer();
				theFileReader = null;
				theFileWriter = null;
				theFileData = null;
			}
		}
	}

	/**
	 * Transforms text serialized by a {@link Format#format(Object)} into an entry for an entity CSV file.
	 *
	 * This involves escaping line break characters (<code>'\n'</code> and <code>'\r'</code>) with backslashes (and doubling backslashes) as
	 * well as CSV-ifying delimiter and quote characters. In CSV, any entry containing a delimiter character must be enclosed in quotes. In
	 * such quote-enclosed entries, actual quotes in the content must be doubled to distinguish them from the end of the entry.
	 *
	 * @param entry The serialized field text
	 * @return The CSV entry to write to the file
	 */
	static CharSequence escapeCsvEntry(String entry) {
		StringBuilder escaped = null;
		boolean hasDelimiter = false, hasQuote = false;
		for (int c = 0; c < entry.length(); c++) {
			char ch = entry.charAt(c);
			switch (ch) {
			case ',':
				hasDelimiter = true;
				// Now that we know there's a delimiter in the entry, we know we have to enclose it in quotes
				if (hasQuote) {
					// Quotes have to be doubled, so we need to go back and double them
					for (int i = 0, j = 0; i < c; i++, j++) {
						if (entry.charAt(c) != escaped.charAt(j) && escaped.charAt(j) == '\\')
							j++;
						if (entry.charAt(c) == '"')
							escaped.insert(j, '"');
					}
				}
				break;
			case '"':
				hasQuote = true;
				if (hasDelimiter) { // Double the quote
					if (escaped != null) {
						escaped = new StringBuilder();
						escaped.append(entry, 0, c);
					}
					escaped.append('"');
				}
				break;
			case '\n':
				ch = 'n';
				//$FALL-THROUGH$
			case '\r':
				ch = 'r';
				//$FALL-THROUGH$
			case '\\':
				if (escaped != null) {
					escaped = new StringBuilder();
					escaped.append(entry, 0, c);
				}
				escaped.append('\\');
				break;
			}
			if (escaped != null)
				escaped.append(ch);
		}
		if (hasDelimiter) {
			return new QuotedCharSeq(escaped != null ? escaped : entry);
		} else
			return escaped != null ? escaped : entry;
	}

	private static class QuotedCharSeq extends AbstractCharSequence {
		private final CharSequence wrapped;

		QuotedCharSeq(CharSequence wrapped) {
			this.wrapped = wrapped;
		}

		@Override
		public int length() {
			return wrapped.length() + 2;
		}

		@Override
		public char charAt(int index) {
			if (index == 0 || index == wrapped.length() + 1)
				return '"';
			return wrapped.charAt(index - 1);
		}
	}

	/**
	 * @param entry The CSV file entry to transform into a {@link Format}-parseable string
	 * @param parser The CSV parser used to parse the entry
	 * @param column The column index of the entry
	 * @return The un-escaped entry
	 * @throws TextParseException If the entry is not escaped correctly
	 */
	protected static String unescapeCsvEntry(String entry, CsvParser parser, int column) throws TextParseException {
		if (entry.isEmpty())
			return entry;
		StringBuilder unescaped = null;
		boolean escaped = false;
		for (int c = 0; c < entry.length(); c++) {
			if (escaped) {
				switch (entry.charAt(c)) {
				case '\\':
					unescaped.append('\\');
					break;
				case 'n':
					unescaped.append('\n');
					break;
				case 'r':
					unescaped.append('\r');
					break;
				default:
					parser.throwParseException(column, c - 1, "Invalid escape sequence: '\\" + entry.charAt(c));
					break;
				}
			} else if (entry.charAt(c) == '\\') {
				escaped = true;
				if (unescaped == null) {
					unescaped = new StringBuilder();
					unescaped.append(entry, 0, c);
				}
			} else if (unescaped != null)
				unescaped.append(entry.charAt(c));
		}
		return unescaped != null ? unescaped.toString() : entry;
	}
}
