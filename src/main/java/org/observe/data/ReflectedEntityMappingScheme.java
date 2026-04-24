package org.observe.data;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.observe.config.ObservableValueSet;
import org.observe.util.EntityReflector;
import org.observe.util.TypeTokens;
import org.qommons.collect.BetterCollection;
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
		else if (EntityReflector.isEntityType(type))
			return EntityReflector.build(typeToken, true)//
				.withSupers(theReflectorCache)//
				.build();
		else
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
		if (ObservableValueSet.class.isAssignableFrom(fieldType))
			return BetterCollection.class;
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
