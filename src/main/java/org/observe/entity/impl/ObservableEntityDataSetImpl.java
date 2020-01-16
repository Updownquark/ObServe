package org.observe.entity.impl;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
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

import org.observe.entity.EntityCondition;
import org.observe.entity.EntityConstraint;
import org.observe.entity.EntityValueAccess;
import org.observe.entity.FieldConstraint;
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

		/* TODO
		 * Defaulted overridden methods (e.g. one of multiple ID fields from a super type is constant for a particular sub-type)
		 */

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

			ObservableEntityFieldBuilder<E, F> field = new ObservableEntityFieldBuilder<>(this, fieldName, type);
			for (ObservableEntityTypeImpl<? super E> parent : theParents) {
				ObservableEntityFieldType<? super E, ?> superField = parent.getFields().getIfPresent(fieldName);
				if (superField != null) {
					if (TypeTokens.get().unwrap(TypeTokens.getRawType(superField.getFieldType())) != void.class
						&& !superField.getFieldType().isAssignableFrom(type))
						throw new IllegalArgumentException("Field " + superField + " cannot be overridden with type " + type);
					field.withOverride((ObservableEntityFieldType<? super E, F>) superField);
				}
			}
			if (theReflector != null && theReflector.getFields().keyIndexTolerant(fieldName) >= 0)
				field.mapTo(theReflector.getFields().get(fieldName).getGetter().getMethod());

			theFields.put(fieldName, field);
			return field;
		}

		public EntitySetBuilder build() {
			Map<String, ObservableEntityFieldBuilder<E, ?>> fieldBuilders = new LinkedHashMap<>(theFields);
			Set<String> idFieldNames = new LinkedHashSet<>();
			for (ObservableEntityFieldBuilder<E, ?> field : fieldBuilders.values()) {
				if (field.isId)
					idFieldNames.add(field.getName());
			}
			if (theParents.isEmpty() && idFieldNames.isEmpty())
				throw new IllegalStateException("No identity fields defined for root-level entity type " + theEntityName);
			for (ObservableEntityTypeImpl<? super E> parent : theParents) {
				for (ObservableEntityFieldType<? super E, ?> field : parent.getFields().allValues()) {
					ObservableEntityFieldBuilder<E, ?> builder = fieldBuilders.get(field.getName());
					if (builder == null) {
						builder = new ObservableEntityFieldBuilder<>(this, field.getName(), field.getFieldType());
						fieldBuilders.put(field.getName(), builder);
					}
					// TODO
				}
			}

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
			QuickSet<String> fieldNames = QuickSet.of(StringUtils.DISTINCT_NUMBER_TOLERANT, tempFields.keySet());
			QuickSet<String> idFieldNames = QuickSet.of(StringUtils.DISTINCT_NUMBER_TOLERANT, tempIdFields.keySet());
			QuickMap<String, ObservableEntityFieldType<? super E, ?>> fields = fieldNames.createMap();
			QuickMap<String, IdentityFieldType<? super E, ?>> idFields = idFieldNames.createMap();
			ObservableEntityTypeImpl<E> entityType = new ObservableEntityTypeImpl<>(theEntitySet, theEntityName,
				Collections.unmodifiableList(theParents), idFields.unmodifiable(), fields.unmodifiable(), theReflector);
			for (ObservableEntityFieldBuilder<E, ?> fieldBuilder : theFields.values()) {
				int fieldIndex = fieldNames.indexOf(fieldBuilder.getName());
				int idIndex = idFieldNames.indexOfTolerant(fieldBuilder.getName());
				ObservableEntityFieldType<E, ?> field = fieldBuilder.buildField(entityType, fieldIndex, idIndex);
				fields.put(fieldIndex, field);
				if (idIndex >= 0)
					idFields.put(idIndex, (IdentityFieldType<E, ?>) field);
			}
			theEntitySet.theEntityTypes.add(entityType);
			if (theJavaType != null && !theEntityName.equals(theJavaType.getSimpleName()))
				theEntitySet.theClassMapping.put(theJavaType, theEntityName);
			isBuilding = false;
			return theSetBuilder;
		}
	}

	public static class ObservableEntityFieldBuilder<E, F> {
		final ObservableEntityTypeBuilder<E> theTypeBuilder;
		final String theName;
		final TypeToken<F> theType;
		List<ObservableEntityFieldType<? super E, F>> theOverrides;
		List<FieldConstraintBuilder<E, F>> theConstraints;
		Method theFieldGetter;
		boolean isId;

		ObservableEntityFieldBuilder(ObservableEntityTypeBuilder<E> typeBuilder, String name, TypeToken<F> type) {
			theTypeBuilder = typeBuilder;
			theName = name;
			theType = type;
		}

		String getName() {
			return theName;
		}

		void withOverride(ObservableEntityFieldType<? super E, F> override) {
			if (theOverrides == null)
				theOverrides = new LinkedList<>();
			theOverrides.add(override);
		}

		public ObservableEntityFieldBuilder<E, F> id() {
			if (!theTypeBuilder.theParents.isEmpty())
				throw new IllegalStateException("ID fields may not be defined on entity types with parents");
			isId = true;
			return this;
		}

		public ObservableEntityFieldBuilder<E, F> withConstraint(String type, String name) {
			if (theConstraints == null)
				theConstraints = new LinkedList<>();
			theConstraints.add(new FieldConstraintBuilder<>(type, name, field -> new SimpleFieldConstraint<>(field, name, type)));
			return this;
		}

		public ObservableEntityFieldBuilder<E, F> withConstraint(String name, Function<EntityCondition<E>, EntityCondition<E>> condition) {
			if (theConstraints == null)
				theConstraints = new LinkedList<>();
			theConstraints.add(new FieldConstraintBuilder<>(EntityConstraint.CHECK, name, field -> {
				return new ConditionalFieldConstraint<>(field, name, condition.apply(new SingleFieldCondition<>(field)));
			}));
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
				return new IdFieldTypeImpl<>(this, entityType, fieldIndex, idIndex);
			else
				return new FieldTypeImpl<>(this, entityType, fieldIndex);
		}

		public ObservableEntityTypeBuilder<E> build() {
			return theTypeBuilder;
		}
	}

	static class FieldConstraintBuilder<E, F> {
		final String constraintType;
		final String name;
		final Function<ObservableEntityFieldType<E, F>, FieldConstraint<E, F>> constraints;

		FieldConstraintBuilder(String constraintType, String name,
			Function<ObservableEntityFieldType<E, F>, FieldConstraint<E, F>> constraints) {
			this.constraintType = constraintType;
			this.name = name;
			this.constraints = constraints;
		}
	}

	static class SingleFieldCondition<E, F> extends EntityCondition<E> {
		private final ObservableEntityFieldType<E, F> theField;

		public SingleFieldCondition(ObservableEntityFieldType<E, F> field) {
			super(field.getEntityType(), Collections.emptyMap());
			theField = field;
		}

		@Override
		public <F2> EntityCondition<E> compare(EntityValueAccess<? super E, F2> field, F2 value, int ltEqGt, boolean withEqual) {
			if (field != theField)
				throw new IllegalArgumentException(
					"Field constraints may only test the target field (" + theField + ") directly: " + field);
			return super.compare(field, value, ltEqGt, withEqual);
		}

		@Override
		public <F2> EntityCondition<E> compareVariable(EntityValueAccess<? super E, F2> field, String variableName, int ltEqGt,
			boolean withEqual) {
			throw new UnsupportedOperationException("Variables are not supported within a field constraint");
		}

		@Override
		protected EntityCondition<E> getNone() {
			return new SingleFieldCondition<>(theField);
		}
	}

	static class FieldTypeImpl<E, F> implements ObservableEntityFieldType<E, F> {
		private final ObservableEntityTypeImpl<E> theEntityType;
		private final TypeToken<F> theFieldType;
		private final String theName;
		private final Method theFieldGetter;
		private final int theFieldIndex;
		private final List<ObservableEntityFieldType<? super E, F>> theOverrides;
		private final List<FieldConstraint<E, F>> theConstraints;

		FieldTypeImpl(ObservableEntityFieldBuilder<E, F> builder, ObservableEntityTypeImpl<E> entity, int fieldIndex) {
			theEntityType = entity;
			theFieldType = builder.theType;
			theName = builder.theName;
			theFieldGetter = builder.theFieldGetter;
			theFieldIndex = fieldIndex;
			if (builder.theOverrides == null)
				theOverrides = Collections.emptyList();
			else {
				List<ObservableEntityFieldType<? super E, F>> newOverrides = new ArrayList<>(builder.theOverrides.size());
				newOverrides.addAll(builder.theOverrides);
				theOverrides = Collections.unmodifiableList(newOverrides);
			}
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

		@Override
		public List<? extends ObservableEntityFieldType<? super E, F>> getOverrides() {
			return theOverrides;
		}

		Method getFieldGetter() {
			return theFieldGetter;
		}

		@Override
		public ObservableEntityField<E, F> getValue(ObservableEntity<? extends E> entity) {
			return (ObservableEntityField<E, F>) entity.getField(this);
		}

		@Override
		public String canAccept(F value) {}

		@Override
		public <T> EntityValueAccess<E, T> dot(Function<? super F, T> attr) {}

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

		IdFieldTypeImpl(ObservableEntityFieldBuilder<E, F> builder, ObservableEntityTypeImpl<E> entityType, int fieldIndex, int idIndex) {
			super(builder, entityType, fieldIndex);
			theIdIndex = idIndex;
		}

		@Override
		public int getIdIndex() {
			return theIdIndex;
		}

		@Override
		public List<? extends IdentityFieldType<? super E, F>> getOverrides() {
			return (List<? extends IdentityFieldType<? super E, F>>) super.getOverrides();
		}
	}
}
