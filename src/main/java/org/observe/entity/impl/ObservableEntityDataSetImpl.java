package org.observe.entity.impl;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

import org.observe.entity.IdentityFieldType;
import org.observe.entity.ObservableEntity;
import org.observe.entity.ObservableEntityDataSet;
import org.observe.entity.ObservableEntityField;
import org.observe.entity.ObservableEntityFieldType;
import org.observe.entity.ObservableEntityType;
import org.observe.util.EntityReflector;
import org.observe.util.EntityReflector.ReflectedField;
import org.observe.util.MethodRetrievingHandler;
import org.observe.util.TypeTokens;
import org.qommons.QommonsUtils;
import org.qommons.StringUtils;
import org.qommons.collect.BetterSortedList;
import org.qommons.collect.BetterSortedSet;
import org.qommons.collect.QuickSet;
import org.qommons.collect.QuickSet.QuickMap;
import org.qommons.tree.BetterTreeSet;

import com.google.common.reflect.TypeToken;

public class ObservableEntityDataSetImpl implements ObservableEntityDataSet {
	private final BetterSortedSet<ObservableEntityTypeImpl<?>> theEntityTypes;
	private final Map<Class<?>, String> theClassMapping;

	private ObservableEntityDataSetImpl() {
		theEntityTypes = new BetterTreeSet<>(true, (et1, et2) -> compareEntityTypes(et1.getEntityName(), et2.getEntityName()));
		theClassMapping = new HashMap<>();
	}

	@Override
	public List<ObservableEntityType<?>> getEntityTypes() {
		return (List<ObservableEntityType<?>>) (List<?>) theEntityTypes;
	}

	@Override
	public ObservableEntityType<?> getEntityType(String entityName) {
		return theEntityTypes.searchValue(et -> compareEntityTypes(entityName, et.getEntityName()),
			BetterSortedList.SortedSearchFilter.OnlyMatch);
	}

	@Override
	public <E> ObservableEntityType<E> getEntityType(Class<E> type) {
		String entityName = theClassMapping.get(type);
		if (entityName == null)
			return null;
		ObservableEntityType<?> entityType = getEntityType(entityName);
		if (entityType != null && entityType.getEntityType() == type)
			return (ObservableEntityType<E>) entityType;
		return null;
	}

	private static int compareEntityTypes(String type1, String type2) {
		return QommonsUtils.compareNumberTolerant(type1, type2, true, true);
	}

	public static EntitySetBuilder build() {
		return new EntitySetBuilder();
	}

	public static class EntitySetBuilder {
		private final ObservableEntityDataSetImpl theEntitySet;
		private boolean isBuilding;

		EntitySetBuilder() {
			theEntitySet = new ObservableEntityDataSetImpl();
			isBuilding = true;
		}

		public <E> ObservableEntityTypeBuilder<E> withEntityType(String entityName) {
			return withEntityType(entityName, null);
		}

		public <E> ObservableEntityTypeBuilder<E> withEntityType(Class<E> entity) {
			return withEntityType(null, EntityReflector.build(TypeTokens.get().of(entity), false).build());
		}

		public <E> ObservableEntityTypeBuilder<E> withEntityType(EntityReflector<E> entity) {
			Class<E> javaType = TypeTokens.getRawType(entity.getType());
			if (javaType.getTypeParameters().length > 0)
				throw new IllegalArgumentException("Cannot create an entity from a parameterized type: " + javaType.getName());
			return withEntityType(null, entity);
		}

		private <E> ObservableEntityTypeBuilder<E> withEntityType(String entityName, EntityReflector<E> reflector) {
			if (!isBuilding)
				throw new IllegalStateException("This builder has already built its entity set");
			Class<E> javaType;
			if (reflector != null) {
				javaType = TypeTokens.getRawType(reflector.getType());
				if (javaType.getTypeParameters().length > 0)
					throw new IllegalArgumentException("Cannot create an entity from a parameterized type: " + javaType.getName());
				if (entityName == null)
					entityName = javaType.getSimpleName();
				if (theEntitySet.theClassMapping.containsKey(javaType))
					throw new IllegalArgumentException("An ObservableEntityType mapped to " + javaType.getName() + " is already defined");
			} else
				javaType = null;
			if (theEntitySet.getEntityType(entityName) != null)
				throw new IllegalArgumentException("Entity type named " + entityName + " already defined");
			return new ObservableEntityTypeBuilder<>(this, theEntitySet, entityName, javaType, reflector);
		}

		public <E> ObservableEntityType<E> getEntityType(Class<E> entity) {
			ObservableEntityType<E> type = theEntitySet.getEntityType(entity);
			if (type == null)
				throw new IllegalArgumentException("No entity type built for " + entity.getName());
			return type;
		}

		public ObservableEntityType<?> getEntityType(String name) {
			ObservableEntityType<?> type = theEntitySet.getEntityType(name);
			if (type == null)
				throw new IllegalArgumentException("No such entity type: " + name);
			return type;
		}

		public ObservableEntityDataSet build() {
			return theEntitySet;
		}
	}

	public static class ObservableEntityTypeBuilder<E> {
		private final EntitySetBuilder theSetBuilder;
		private final ObservableEntityDataSetImpl theEntitySet;
		// private ObservableEntityTypeImpl<E> theType;
		private final Class<E> theJavaType;
		private final EntityReflector<E> theReflector;
		private final E theProxy;
		private final MethodRetrievingHandler theProxyHandler;
		private String theEntityName;

		private final List<ObservableEntityTypeImpl<? super E>> theParents;
		private final Map<String, ObservableEntityFieldBuilder<E, ?>> theFields;

		private boolean isBuilding;

		ObservableEntityTypeBuilder(EntitySetBuilder setBuilder, ObservableEntityDataSetImpl entitySet, String entityName,
			Class<E> entityType, EntityReflector<E> reflector) {
			theSetBuilder = setBuilder;
			theEntitySet = entitySet;
			theJavaType = entityType;
			theReflector = reflector;
			theEntityName = entityName;
			if (theJavaType != null) {
				theProxyHandler = new MethodRetrievingHandler();
				theProxy = (E) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[] { theJavaType }, theProxyHandler);
			} else {
				theProxyHandler = null;
				theProxy = null;
			}

			theParents = new LinkedList<>();
			theFields = new LinkedHashMap<>();
			isBuilding = true;
		}

		public ObservableEntityTypeBuilder<E> withName(String name) {
			theEntityName = name;
			return this;
		}

		public ObservableEntityTypeBuilder<E> fillSupersFromClass() {
			if (!isBuilding)
				throw new IllegalStateException("This builder has already built its entity type");
			else if (theReflector == null)
				throw new IllegalStateException("This entity type is not mapped to a java type");
			for (EntityReflector<? super E> superRef : theReflector.getSuper()) {
				Class<? super E> superClass = TypeTokens.getRawType(superRef.getType());
				if (superClass.getTypeParameters().length > 0)
					continue; // Entities cannot have type parameters
				ObservableEntityType<? super E> superEntity = theSetBuilder.getEntityType(superClass);
				if (superEntity == null) {
					// If it hasn't been declared, create it
					theSetBuilder.withEntityType(superRef).fillSupersFromClass().fillFieldsFromClass().build();
					superEntity = theSetBuilder.getEntityType(superClass);
				}
				withSuper(superEntity);
			}
			return this;
		}

		public ObservableEntityTypeBuilder<E> fillFieldsFromClass() {
			if (!isBuilding)
				throw new IllegalStateException("This builder has already built its entity type");
			else if (theReflector == null)
				throw new IllegalStateException("This entity type is not mapped to a java type");
			for (int f = 0; f < theReflector.getFields().keySize(); f++) {
				ReflectedField<E, ?> field = theReflector.getFields().get(f);
				boolean useSuper = false;
				for (ObservableEntityTypeImpl<? super E> parent : theParents) {
					if (parent.getFields().keySet().contains(field.getName())) {
						useSuper = true;
						break;
					}
				}
				if (!useSuper)
					buildField(field);
			}
			return this;
		}

		private <F> void buildField(ReflectedField<E, F> field) {
			ObservableEntityFieldBuilder<E, F> fieldBuilder = withField(field.getName(), field.getType());
			if (field.isId())
				fieldBuilder.id();
			fieldBuilder.mapTo(field.getGetter().getMethod());
			fieldBuilder.build();
		}

		public ObservableEntityTypeBuilder<E> withSuper(Class<? super E> parent) {
			return withSuper(EntityReflector.build(TypeTokens.get().of(parent), false).build());
		}

		public ObservableEntityTypeBuilder<E> withSuper(EntityReflector<? super E> parent) {
			Class<? super E> superType = TypeTokens.getRawType(parent.getType());
			ObservableEntityType<? super E> superEntity = theSetBuilder.getEntityType(superType);
			if (superEntity == null) {
				theSetBuilder.withEntityType(parent).fillFieldsFromClass().build();
				superEntity = theSetBuilder.getEntityType(superType);
			}
			return withSuper(superEntity);
		}

		public ObservableEntityTypeBuilder<E> withSuper(ObservableEntityType<?> parent) {
			if (!isBuilding)
				throw new IllegalStateException("This builder has already built its entity type");
			else if (!theFields.isEmpty())
				throw new IllegalStateException("Parent(s) must be defined before any fields");
			if (!(parent instanceof ObservableEntityTypeImpl) || theEntitySet.getEntityType(parent.getEntityName()) != parent)
				throw new IllegalArgumentException("An entity type's parent must be present in the same entity set");
			if (theJavaType != null)
				checkSuperType(parent);
			theParents.add((ObservableEntityTypeImpl<? super E>) parent);
			return this;
		}

		private void checkSuperType(ObservableEntityType<?> parent) {
			if (parent.getEntityType() != null) {
				if (!parent.getEntityType().isAssignableFrom(theJavaType))
					throw new IllegalArgumentException("Entity type " + parent.getEntityName() + " (" + parent.getEntityType().getName()
						+ ") cannot be a super type of " + theEntityName + " (" + theJavaType.getName() + ")");
				return;
			}
			for (ObservableEntityType<?> parentParent : parent.getSupers())
				checkSuperType(parentParent);
		}

		public <F> ObservableEntityFieldBuilder<E, F> withField(Function<? super E, F> fieldGetter) {
			if (theJavaType == null)
				throw new IllegalStateException("This method can only be used with a java-typed entity");
			fieldGetter.apply(theProxy);
			Method method = theProxyHandler.getInvoked();
			if (method == null)
				throw new IllegalArgumentException(fieldGetter + " is not a getter method for a " + theJavaType.getName() + " field");
			String name = method.getName();
			if (!name.startsWith("get") || name.length() == 3)
				throw new IllegalArgumentException(name + " is not a getter method for a " + theJavaType.getName() + " field");
			name = name.substring(3);
			if (Character.isUpperCase(name.charAt(0)))
				name = Character.toLowerCase(name.charAt(0)) + name.substring(1);
			return (ObservableEntityFieldBuilder<E, F>) withField(name, TypeToken.of(method.getGenericReturnType())).mapTo(method);
		}

		public <F> ObservableEntityFieldBuilder<E, F> withField(String fieldName, TypeToken<F> type) {
			if (!isBuilding)
				throw new IllegalStateException("This builder has already built its entity type");
			else if (theFields.containsKey(fieldName))
				throw new IllegalArgumentException("A field named " + theEntityName + "." + fieldName + " has already been defined");
			for (ObservableEntityTypeImpl<? super E> parent : theParents) {
				ObservableEntityFieldType<? super E, ?> superField = parent.getFields().getIfPresent(fieldName);
				if (superField != null && TypeTokens.get().unwrap(TypeTokens.getRawType(superField.getFieldType())) != void.class
					&& !superField.getFieldType().isAssignableFrom(type))
					throw new IllegalArgumentException("Field " + superField + " cannot be overridden with type " + type);
			}

			ObservableEntityFieldBuilder<E, F> field = new ObservableEntityFieldBuilder<>(this, fieldName, type);
			theFields.put(fieldName, field);
			return field;
		}

		public EntitySetBuilder build() {
			QuickMap<String, IdentityFieldType<? super E, ?>> idFields;
			QuickMap<String, ObservableEntityFieldType<? super E, ?>> fields;
			Map<String, ObservableEntityFieldType<? super E, ?>> tempFields = new LinkedHashMap<>();
			Map<String, IdentityFieldType<? super E, ?>> tempIdFields = new LinkedHashMap<>();
			if (theParents.isEmpty())
				throw new IllegalStateException("No identity fields defined for entity type " + theEntityName);
			for (ObservableEntityTypeImpl<? super E> parent : theParents) {
				for (ObservableEntityFieldType<? super E, ?> field : parent.getFields().allValues()) {
					tempFields.putIfAbsent(field.getName(), field);
					if (field instanceof IdentityFieldType)
						tempIdFields.putIfAbsent(field.getName(), (IdentityFieldType<? super E, ?>) field);
				}
			}
			for(ObservableEntityFieldBuilder<E, ?> fieldBuilder : theFields.values()){
				field=fieldBuilder.buildField(
			}
			idFields = QuickMap.of(tempIdFields, StringUtils.DISTINCT_NUMBER_TOLERANT).unmodifiable();
			fields = QuickMap.of(tempFields, StringUtils.DISTINCT_NUMBER_TOLERANT).unmodifiable();
			ObservableEntityTypeImpl<E> entityType = new ObservableEntityTypeImpl<>(theEntitySet, theEntityName,
				Collections.unmodifiableList(theParents), idFields.unmodifiable(), fields.unmodifiable(), theReflector);
			theEntitySet.theEntityTypes.add(entityType);
			if (theJavaType != null && !theEntityName.equals(theJavaType.getSimpleName()))
				theEntitySet.theClassMapping.put(theJavaType, theEntityName);
			isBuilding = false;
			return theSetBuilder;
		}
	}

	public static class ObservableEntityFieldBuilder<E, F> {
		private final ObservableEntityTypeBuilder<E> theTypeBuilder;
		private final String theName;
		private final TypeToken<F> theType;
		private Method theFieldGetter;
		private boolean isId;

		ObservableEntityFieldBuilder(ObservableEntityTypeBuilder<E> typeBuilder, String name, TypeToken<F> type) {
			theTypeBuilder = typeBuilder;
			theName = name;
			theType = type;
		}

		public ObservableEntityFieldBuilder<E, F> id() {
			if (!theTypeBuilder.theParents.isEmpty())
				throw new IllegalStateException("ID fields may not be defined on entity types with parents");
			isId = true;
			return this;
		}

		public ObservableEntityFieldBuilder<E, F> mapTo(Function<? super E, ? extends F> fieldGetter) {
			Class<E> type = theTypeBuilder.theJavaType;
			if (type == null)
				throw new IllegalStateException("This method can only be used with a java-typed entity");
			Method method = ObservableEntityUtils.getField(type, fieldGetter);
			if (method == null)
				throw new IllegalArgumentException(fieldGetter + " is not a getter method for a " + type.getName() + " field");
			String name = method.getName();
			if (!name.startsWith("get") || name.length() == 3)
				throw new IllegalArgumentException(name + " is not a getter method for a " + type.getName() + " field");
			return mapTo(method);
		}

		ObservableEntityFieldBuilder<E, F> mapTo(Method fieldGetter) {
			theFieldGetter = fieldGetter;
			return this;
		}

		ObservableEntityFieldType<E, F> buildField(ObservableEntityTypeImpl<E> entityType, int fieldIndex, int idIndex) {
			if (isId)
				return new IdFieldTypeImpl<>(entityType, theType, theName, theFieldGetter, fieldIndex, idIndex);
			else
				return new FieldTypeImpl<>(entityType, theType, theName, theFieldGetter, fieldIndex);
		}

		public ObservableEntityTypeBuilder<E> build() {
			return theTypeBuilder;
		}
	}

	static class FieldTypeImpl<E, F> implements ObservableEntityFieldType<E, F> {
		private final ObservableEntityTypeImpl<E> theEntityType;
		private final TypeToken<F> theFieldType;
		private final String theName;
		private final Method theFieldGetter;
		private final int theFieldIndex;

		FieldTypeImpl(ObservableEntityTypeImpl<E> entityType, TypeToken<F> fieldType, String name, Method fieldGetter, int fieldIndex) {
			theEntityType = entityType;
			theFieldType = fieldType;
			theName = name;
			theFieldGetter = fieldGetter;
			theFieldIndex = fieldIndex;
		}

		@Override
		public ObservableEntityType<E> getEntityType() {
			return theEntityType;
		}

		@Override
		public TypeToken<F> getFieldType() {
			return theFieldType;
		}

		@Override
		public String getName() {
			return theName;
		}

		@Override
		public int getFieldIndex() {
			return theFieldIndex;
		}

		Method getFieldGetter() {
			return theFieldGetter;
		}

		@Override
		public ObservableEntityField<E, F> getField(ObservableEntity<? extends E> entity) {
			return (ObservableEntityField<E, F>) entity.getField(this);
		}

		@Override
		public int hashCode() {
			return Objects.hash(theEntityType, theFieldIndex);
		}

		@Override
		public boolean equals(Object obj) {
			if (obj == this)
				return true;
			else if (!(obj instanceof ObservableEntityFieldType))
				return false;
			ObservableEntityFieldType<?, ?> other = (ObservableEntityFieldType<?, ?>) obj;
			return theEntityType.equals(other.getEntityType()) && theFieldIndex == other.getFieldIndex();
		}

		@Override
		public String toString() {
			return theEntityType.getEntityName() + "." + theName;
		}
	}

	static class IdFieldTypeImpl<E, F> extends FieldTypeImpl<E, F> implements IdentityFieldType<E, F> {
		private final int theIdIndex;

		IdFieldTypeImpl(ObservableEntityTypeImpl<E> entityType, TypeToken<F> fieldType, String name, Method fieldGetter, int fieldIndex,
			int idIndex) {
			super(entityType, fieldType, name, fieldGetter, fieldIndex);
			theIdIndex = idIndex;
		}

		@Override
		public int getIdIndex() {
			return theIdIndex;
		}
	}
}
