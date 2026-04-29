package org.observe.data;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.observe.assoc.ObservableMap;
import org.observe.assoc.ObservableMultiMap;
import org.observe.assoc.ObservableSortedMap;
import org.observe.assoc.ObservableSortedMultiMap;
import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableSet;
import org.observe.collect.ObservableSortedCollection;
import org.observe.collect.ObservableSortedSet;
import org.observe.config.SyncValueSet;
import org.observe.util.EntityReflector;
import org.observe.util.TypeTokens;
import org.qommons.collect.BetterList;
import org.qommons.collect.BetterMap;
import org.qommons.collect.BetterMultiMap;
import org.qommons.collect.BetterSet;
import org.qommons.collect.BetterSortedList;
import org.qommons.collect.BetterSortedMap;
import org.qommons.collect.BetterSortedMultiMap;
import org.qommons.collect.BetterSortedSet;
import org.qommons.data.mapping.EntityFieldMapping;
import org.qommons.data.mapping.EntityTypeMapping;
import org.qommons.data.mapping.EntityTypeSetMapping;
import org.qommons.data.types.FieldType;

import com.google.common.reflect.TypeToken;

public class ReflectedEntityMappingScheme implements EntityTypeSetMapping.EntityMappingScheme<EntityReflector<?>> {
	private final Map<TypeToken<?>, EntityReflector<?>> theReflectorCache;

	public ReflectedEntityMappingScheme() {
		theReflectorCache = new HashMap<>();
	}

	public Map<TypeToken<?>, EntityReflector<?>> getReflectorCache() {
		return theReflectorCache;
	}

	@Override
	public EntityReflector<?> isEntity(Class<?> type) {
		TypeToken<?> typeToken = TypeTokens.get().of(type);
		EntityReflector<?> preLoaded = theReflectorCache.get(typeToken);
		if (preLoaded != null)
			return preLoaded;
		else if (EntityReflector.isEntityType(type)) {
			return theReflectorCache.computeIfAbsent(typeToken, tt -> EntityReflector.build(tt, true)//
				.withSupers(theReflectorCache)//
				.build());
		} else
			return null;
	}

	@Override
	public String getEntityName(Class<?> type, EntityReflector<?> entity) {
		return type.getSimpleName();
	}

	@Override
	public String getField(EntityReflector<?> entity, Method getter) {
		EntityReflector.MethodInterpreter<?, ?> interpreter = entity.getInterpreter(getter);
		if (interpreter instanceof EntityReflector.FieldGetter<?, ?>)
			return ((EntityReflector.FieldGetter<?, ?>) interpreter).getField().getName();
		else
			return null;
	}

	@Override
	public Class<?> getGenericRawType(Class<?> fieldType) {
		// Support value sets
		if (fieldType.isAssignableFrom(SyncValueSet.class))
			return BetterSortedSet.class;
		// Support observable structures
		else if (fieldType.isAssignableFrom(ObservableCollection.class))
			return BetterList.class;
		else if (fieldType.isAssignableFrom(ObservableSet.class))
			return BetterSet.class;
		else if (fieldType.isAssignableFrom(ObservableSortedCollection.class))
			return BetterSortedList.class;
		else if (fieldType.isAssignableFrom(ObservableSortedSet.class))
			return BetterSortedSet.class;
		else if (fieldType.isAssignableFrom(ObservableMap.class))
			return BetterMap.class;
		else if (fieldType.isAssignableFrom(ObservableSortedMap.class))
			return BetterSortedMap.class;
		else if (fieldType.isAssignableFrom(ObservableMultiMap.class))
			return BetterMultiMap.class;
		else if (fieldType.isAssignableFrom(ObservableSortedMultiMap.class))
			return BetterSortedMultiMap.class;
		else
			return fieldType;
	}

	public static List<String> checkTypeSet(EntityTypeSetMapping types) {
		List<String> errors = new ArrayList<>();
		for (EntityTypeMapping<?> type : types.getEntityTypes().values()) {
			checkType(type, errors);
		}
		return errors;
	}

	private static void checkType(EntityTypeMapping<?> type, List<String> errors) {
		for (EntityFieldMapping<?, ?> field : type.getFields()) {
			checkField(field, errors);
		}
	}

	private static void checkField(EntityFieldMapping<?, ?> field, List<String> errors) {
		FieldType<?> type = field.getGenericField().getType();
		if (type instanceof FieldType.ParameterizedType) {
			if (((FieldType.ParameterizedType<?>) type).isComplex())
				errors.add("Complex field types are not supported: " + field);
		}
	}
}
