package org.observe.entity.impl;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

import org.observe.entity.EntityCreator;
import org.observe.entity.EntitySelection;
import org.observe.entity.IdentityFieldType;
import org.observe.entity.ObservableEntity;
import org.observe.entity.ObservableEntityField;
import org.observe.entity.ObservableEntityFieldType;
import org.observe.entity.ObservableEntitySet;
import org.observe.entity.ObservableEntityType;
import org.observe.util.TypeTokens;
import org.qommons.QommonsUtils;
import org.qommons.collect.BetterSortedSet;
import org.qommons.collect.ParameterSet;
import org.qommons.collect.ParameterSet.ParameterMap;
import org.qommons.tree.BetterTreeSet;

import com.google.common.reflect.TypeToken;

public class ObservableEntitySetImpl implements ObservableEntitySet {
	private final BetterSortedSet<ObservableEntityTypeImpl<?>> theEntityTypes;
	private final Map<Class<?>, String> theWeirdNames;

	private ObservableEntitySetImpl() {
		theEntityTypes = new BetterTreeSet<>(true, (et1, et2) -> compareEntityTypes(et1.getEntityName(), et2.getEntityName()));
		theWeirdNames = new HashMap<>();
	}

	@Override
	public List<ObservableEntityType<?>> getEntityTypes() {
		return (List<ObservableEntityType<?>>) (List<?>) theEntityTypes;
	}

	@Override
	public ObservableEntityType<?> getEntityType(String entityName) {
		return theEntityTypes.searchValue(et -> compareEntityTypes(entityName, et.getEntityName()),
			BetterSortedSet.SortedSearchFilter.OnlyMatch);
	}

	@Override
	public <E> ObservableEntityType<E> getEntityType(Class<E> type) {
		String entityName = theWeirdNames.get(type);
		if (entityName == null)
			entityName = type.getSimpleName();
		ObservableEntityType<?> entityType = getEntityType(entityName);
		if (entityType != null && entityType.getEntityType() == type)
			return (ObservableEntityType<E>) entityType;
		return null;
	}

	@Override
	public <E> EntitySelection<E> select(ObservableEntityType<E> type) {
		return ((ObservableEntityTypeImpl<E>) type).select();
	}

	@Override
	public <E> EntityCreator<E> create(ObservableEntityType<E> type) {
		return ((ObservableEntityTypeImpl<E>) type).create();
	}

	private static int compareEntityTypes(String type1, String type2) {
		return QommonsUtils.compareNumberTolerant(type1, type2, true, true);
	}

	public static EntitySetBuilder build() {
		return new EntitySetBuilder();
	}

	public static class EntitySetBuilder {
		private final ObservableEntitySetImpl theEntitySet;
		private boolean isBuilding;

		EntitySetBuilder() {
			theEntitySet = new ObservableEntitySetImpl();
			isBuilding = true;
		}

		public <E> ObservableEntityTypeBuilder<E> withEntityType(Class<E> entity) {
			return withEntityType(entity, entity.getSimpleName());
		}

		public <E> ObservableEntityTypeBuilder<E> withEntityType(String entityName) {
			return withEntityType(null, entityName);
		}

		public <E> ObservableEntityTypeBuilder<E> withEntityType(Class<E> entity, String entityName) {
			if (!isBuilding)
				throw new IllegalStateException("This builder has already built its entity set");
			if (theEntitySet.getEntityType(entityName) != null)
				throw new IllegalArgumentException("Entity type named " + entityName + " already defined");
			if (entity != null && theEntitySet.theWeirdNames.containsKey(entity))
				throw new IllegalArgumentException("An ObservableEntityType mapped to " + entity.getName() + " is already defined");
			return new ObservableEntityTypeBuilder<>(this, theEntitySet, entity, entityName);
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

		public ObservableEntitySet build() {
			return theEntitySet;
		}
	}

	public static class ObservableEntityTypeBuilder<E> {
		private final EntitySetBuilder theSetBuilder;
		private final ObservableEntitySetImpl theEntitySet;
		// private ObservableEntityTypeImpl<E> theType;
		private final Class<E> theJavaType;
		private final E theProxy;
		private final MethodRetrievingHandler theProxyHandler;
		private final String theEntityName;

		private ObservableEntityTypeImpl<? super E> theParent;
		private Map<String, ObservableEntityFieldBuilder<E, ?>> theIdFields;
		private final Map<String, ObservableEntityFieldBuilder<E, ?>> theFields;

		private boolean isBuilding;

		ObservableEntityTypeBuilder(EntitySetBuilder setBuilder, ObservableEntitySetImpl entitySet, Class<E> entityType,
			String entityName) {
			theSetBuilder = setBuilder;
			theEntitySet = entitySet;
			theJavaType = entityType;
			theEntityName = entityName;
			if (theJavaType != null) {
				theProxyHandler = new MethodRetrievingHandler();
				theProxy = (E) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[] { theJavaType }, theProxyHandler);
			} else {
				theProxyHandler = null;
				theProxy = null;
			}

			theFields = new LinkedHashMap<>();
			isBuilding = true;
		}

		public ObservableEntityTypeBuilder<E> withParent(ObservableEntityType<?> parent) {
			if (!isBuilding)
				throw new IllegalStateException("This builder has already built its entity type");
			else if (theParent != null)
				throw new IllegalStateException("Parent is already defined");
			else if (!theFields.isEmpty())
				throw new IllegalStateException("Parent must be defined before any fields");
			if (!(parent instanceof ObservableEntityTypeImpl) || theEntitySet.getEntityType(parent.getEntityName()) != parent)
				throw new IllegalArgumentException("An entity type's parent must be present in the same entity set");
			if (theJavaType != null) {
				ObservableEntityType<?> typedParent = parent;
				while (typedParent != null && typedParent.getEntityType() == null)
					typedParent = typedParent.getParent();
				if (typedParent != null && !typedParent.getEntityType().isAssignableFrom(theJavaType))
					throw new IllegalArgumentException("Entity type " + parent.getEntityName() + " (" + parent.getEntityType().getName()
						+ ") cannot be a super type of " + theEntityName + " (" + theJavaType.getName() + ")");
			}
			theParent = (ObservableEntityTypeImpl<? super E>) parent;
			return this;
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
			ObservableEntityFieldType<? super E, ?> superField = theParent.getFields().getIfPresent(fieldName);
			if (superField != null && TypeTokens.get().unwrap(TypeTokens.getRawType(superField.getFieldType())) != void.class
				&& !superField.getFieldType().isAssignableFrom(type))
				throw new IllegalArgumentException("Field " + superField + " cannot be overridden with type " + type);
			return new ObservableEntityFieldBuilder<>(this, fieldName, type);
		}

		public EntitySetBuilder build() {
			ParameterMap<IdentityFieldType<? super E, ?>> idFields;
			ParameterMap<ObservableEntityFieldType<? super E, ?>> fields;
			if (theParent != null) {
				idFields = (ParameterMap<IdentityFieldType<? super E, ?>>) (ParameterMap<?>) theParent.getIdentityFields();
				Set<String> otherFieldNames = new LinkedHashSet<>();
				otherFieldNames.addAll(theParent.getFields().keySet());
				otherFieldNames.addAll(theFields.keySet());
				fields = ParameterSet.of(otherFieldNames).createMap();
				for (int i = 0; i < theParent.getFields().keySet().size(); i++)
					fields.put(theParent.getFields().keySet().get(i), theParent.getFields().get(i));
			} else {
				if (theIdFields == null)
					throw new IllegalStateException("No identity fields defined for entity type " + theEntityName);
				idFields = ParameterSet.of(theIdFields.keySet()).createMap();
				fields = ParameterSet.of(theFields.keySet()).createMap();
			}
			ObservableEntityTypeImpl<E> entityType = new ObservableEntityTypeImpl<>(theEntitySet, theEntityName, theJavaType, theParent, //
				idFields.unmodifiable(), fields.unmodifiable(), theProxy, theProxyHandler);
			if (theParent == null) {
				for (int i = 0; i < idFields.keySet().size(); i++)
					idFields.put(i, (IdentityFieldType<? super E, ?>) theIdFields.get(idFields.keySet().get(i)).buildField(entityType, i));
			}
			for (int i = 0; i < fields.keySet().size(); i++)
				fields.put(i, theFields.get(fields.keySet().get(i)).buildField(entityType, i));
			theEntitySet.theEntityTypes.add(entityType);
			if (theJavaType != null && !theEntityName.equals(theJavaType.getSimpleName()))
				theEntitySet.theWeirdNames.put(theJavaType, theEntityName);
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
			if (theTypeBuilder.theParent != null)
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

		ObservableEntityFieldType<E, F> buildField(ObservableEntityTypeImpl<E> entityType, int fieldIndex) {
			if (isId)
				return new IdFieldTypeImpl<>(entityType, theType, theName, theFieldGetter, fieldIndex);
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
			// TODO Auto-generated method stub
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
		IdFieldTypeImpl(ObservableEntityTypeImpl<E> entityType, TypeToken<F> fieldType, String name, Method fieldGetter, int fieldIndex) {
			super(entityType, fieldType, name, fieldGetter, fieldIndex);
		}
	}
}
