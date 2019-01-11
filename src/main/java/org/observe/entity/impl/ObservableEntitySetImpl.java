package org.observe.entity.impl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.observe.entity.EntityCreator;
import org.observe.entity.EntitySelection;
import org.observe.entity.IdentityFieldType;
import org.observe.entity.ObservableEntityFieldType;
import org.observe.entity.ObservableEntitySet;
import org.observe.entity.ObservableEntityType;
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
		private final Class<E> theEntityType;
		private final String theEntityName;

		private ObservableEntityTypeImpl<? super E> theParent;
		private Map<String, IdentityFieldType<? super E, ?>> theIdFields;
		private final Map<String, ObservableEntityFieldType<? super E, ?>> theFields;

		private boolean isBuilding;

		ObservableEntityTypeBuilder(EntitySetBuilder setBuilder, ObservableEntitySetImpl entitySet, Class<E> entityType,
			String entityName) {
			theSetBuilder = setBuilder;
			theEntitySet = entitySet;
			theEntityType = entityType;
			theEntityName = entityName;

			theFields = new HashMap<>();
			isBuilding = true;
		}

		public ObservableEntityTypeBuilder<E> withParent(ObservableEntityType<?> parent) {
			if (!isBuilding)
				throw new IllegalStateException("This builder has already built its entity type");
			else if (theParent != null)
				throw new IllegalStateException("Parent is already defined");
			else if (!theFields.isEmpty())
				throw new IllegalStateException("Parent must be defines before any sub-type fields");
			if (!(parent instanceof ObservableEntityTypeImpl) || theEntitySet.getEntityType(parent.getEntityName()) != parent)
				throw new IllegalArgumentException("An entity type's parent must be present in the same entity set");
			ObservableEntityTypeImpl<?> p = (ObservableEntityTypeImpl<?>) parent;
			if (theEntityType != null) {
				ObservableEntityType<?> typedParent = parent;
				while (typedParent != null && typedParent.getEntityType() == null)
					typedParent = typedParent.getParent();
				if (typedParent != null && !typedParent.getEntityType().isAssignableFrom(theEntityType))
					throw new IllegalArgumentException("Entity type " + parent.getEntityName() + " (" + parent.getEntityType().getName()
						+ ") cannot be a super type of " + theEntityName + " (" + theEntityType.getName() + ")");
			}
			theParent = (ObservableEntityTypeImpl<? super E>) parent;
			for (ObservableEntityFieldType<? super E, ?> field : theParent.getFields().values())
				theFields.put(field.getName(), field);
			return this;
		}

		public <F> ObservableEntityFieldBuilder<E, F> withField(Function<? super E, F> fieldGetter) {}

		public <F> ObservableEntityFieldBuilder<E, F> withField(String fieldName, TypeToken<F> type) {
			if (!isBuilding)
				throw new IllegalStateException("This builder has already built its entity type");
			else if (theFields.containsKey(fieldName))
				throw new IllegalArgumentException("A field named " + theEntityName + "." + fieldName + " has already been defined");
			return new ObservableEntityFieldBuilder<>(this, fieldName, type);
		}

		private <F> void addField(ObservableEntityFieldType<E, F> field) {
			if (field instanceof IdentityFieldType) {
				if (theIdFields == null)
					theIdFields = new HashMap<>();
				theIdFields.put(field.getName(), (IdentityFieldType<? super E, ?>) field);
			} else
				theFields.put(field.getName(), field);
		}

		public EntitySetBuilder build() {
			ParameterMap<IdentityFieldType<? super E, ?>> idFields;
			if (theParent != null)
				idFields = (ParameterMap<IdentityFieldType<? super E, ?>>) (ParameterMap<?>) theParent.getIdentityFields();
			else {
				if (theIdFields == null)
					throw new IllegalStateException("No identity fields defined for entity type " + theEntityName);
				idFields = ParameterSet.of(theIdFields.keySet()).createMap();
				for (int i = 0; i < idFields.keySet().size(); i++)
					idFields.put(i, theIdFields.get(idFields.keySet().get(i)));
				idFields = idFields.unmodifiable();
			}
			ParameterMap<ObservableEntityFieldType<? super E, ?>> fields = ParameterSet.of(theFields.keySet()).createMap();
			for (int i = 0; i < fields.keySet().size(); i++)
				fields.put(i, theFields.get(fields.keySet().get(i)));
			ObservableEntityTypeImpl<E> entityType = new ObservableEntityTypeImpl<>(theEntitySet, theEntityName, theEntityType, theParent, //
				idFields, fields.unmodifiable());
			theEntitySet.theEntityTypes.add(entityType);
			if (theEntityType != null && !theEntityName.equals(theEntityType.getSimpleName()))
				theEntitySet.theWeirdNames.put(theEntityType, theEntityName);
			isBuilding = false;
			return theSetBuilder;
		}
	}

	public static class ObservableEntityFieldBuilder<E, F> {
		private final ObservableEntityTypeBuilder<E> theTypeBuilder;
		private final String theName;
		private final TypeToken<F> theType;
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

		public ObservableEntityFieldBuilder<E, F> mapTo(Function<? super E, ? extends F> fieldGetter) {}

		public ObservableEntityTypeBuilder<E> build() {
			ObservableEntityFieldType<E, F> field;
			if (isId)
				field = new IdentityFieldType<>(theTypeBuilder.theType, theName, theType, 0);
			else {
				// field=new
			}
		}
	}
}
