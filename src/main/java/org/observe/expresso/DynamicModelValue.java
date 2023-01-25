package org.observe.expresso;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.observe.expresso.ModelType.HollowModelValue;
import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ValueContainer;
import org.observe.expresso.ObservableModelSet.ValueCreator;
import org.observe.util.TypeTokens;
import org.qommons.Named;
import org.qommons.collect.BetterList;
import org.qommons.config.QonfigAddOn;
import org.qommons.config.QonfigAttributeDef;
import org.qommons.config.QonfigChildDef;
import org.qommons.config.QonfigElement;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigEvaluationException;
import org.qommons.config.QonfigToolkit;
import org.qommons.config.QonfigValueType;
import org.qommons.config.SpecificationType;

/**
 * A dynamic (interpreted-value-specific) model value
 *
 * @param <M> The model type of the value
 * @param <MV> The type of the value
 */
public interface DynamicModelValue<M, MV extends M> extends ValueContainer<M, MV>, Named {
	/** The definition of a dynamic model value */
	public static class Identity implements Named {
		private final QonfigElementOrAddOn theOwner;
		private final String theName;
		private final QonfigAttributeDef theNameAttribute;
		private final String theType;
		private final QonfigAttributeDef theSourceAttribute;
		private final boolean isSourceValue;
		private final QonfigElement theDeclaration;

		/**
		 * @param owner The type declaring the value
		 * @param name The declared name of the value (may be null if <code>nameAttribute</code> is defined)
		 * @param nameAttribute The attribute that determines the name of this value (null if <code>name</code> is defined)
		 * @param type The string representation of the type for this value. Null if the type is variable.
		 * @param sourceAttribute If defined, the this value will be that evaluated from this expression-typed attribute
		 * @param sourceValue If true, this value will be that evaluated from the expression-typed {@link QonfigElement#getValue()} of the
		 *        element
		 * @param declaration The element that declares this value
		 */
		public Identity(QonfigElementOrAddOn owner, String name, QonfigAttributeDef nameAttribute, String type,
			QonfigAttributeDef sourceAttribute, boolean sourceValue, QonfigElement declaration) {
			theOwner = owner;
			theName = name;
			theNameAttribute = nameAttribute;
			theType = type;
			theSourceAttribute = sourceAttribute;
			isSourceValue = sourceValue;
			theDeclaration = declaration;
		}

		/** @return The type declaring this value */
		public QonfigElementOrAddOn getOwner() {
			return theOwner;
		}

		/** @return The declared name of the value (may be null if <code>nameAttribute</code> is defined) */
		@Override
		public String getName() {
			if (theName != null)
				return theName;
			else
				return "{" + theNameAttribute + "}";
		}

		/** @return The attribute that determines the name of this value (null if <code>name</code> is defined) */
		public QonfigAttributeDef getNameAttribute() {
			return theNameAttribute;
		}

		/** @return The string representation of the type for this value. Null if the type is variable. */
		public String getType() {
			return theType;
		}

		/** @return If defined, the this value will be that evaluated from this expression-typed attribute */
		public QonfigAttributeDef getSourceAttribute() {
			return theSourceAttribute;
		}

		/** @return If true, this value will be that evaluated from the expression-typed {@link QonfigElement#getValue()} of the element */
		public boolean isSourceValue() {
			return isSourceValue;
		}

		/** @return The metadata that is the declaration for this dynamic value */
		public QonfigElement getDeclaration() {
			return theDeclaration;
		}

		@Override
		public String toString() {
			StringBuilder str = new StringBuilder(theOwner.toString()).append('.').append(theName);
			if (theType != null)
				str.append('(').append(theType).append(')');
			if (theSourceAttribute != null)
				str.append("<=").append(theSourceAttribute);
			else if (isSourceValue)
				str.append("<=<value>");
			return str.toString();
		}
	}

	/**
	 * Dynamic values in models should always be {@link ObservableModelSet.Builder#withMaker(String, ValueCreator) added} as an instance of
	 * this type.
	 *
	 * @param <M> The model type of the dynamic value
	 * @param <MV> The type of the dynamic value
	 */
	public interface Creator<M, MV extends M> extends ObservableModelSet.IdentifableValueCreator<M, MV> {
		@Override
		Identity getIdentity();
	}

	/**
	 * @param expresso The toolkit to get expresso types from
	 * @param type The element type to get the dynamic values for
	 * @param values The map to add the dynamic values into
	 * @return The identities/definitions of all dynamic values defined on the given type, grouped by name/name attribute
	 */
	public static Map<String, Identity> getDynamicValues(QonfigToolkit expresso, QonfigElementOrAddOn type, Map<String, Identity> values) {
		return Impl.getDynamicValues(expresso, null, type, values);
	}

	/** @return The declared definition of this dynamic value */
	public Identity getDeclaration();

	@Override
	default String getName() {
		return getDeclaration().getName();
	}

	/**
	 * Called by some implementation to satisfy a metadata-declared dynamic (interpreted-value-specific) model value.
	 *
	 * @param <M> The model type of the value
	 * @param <MV> The type of the value
	 * @param name The name of the value
	 * @param type The type of the value
	 * @param model The model instance to satisfy the value in (should be {@link ExpressoQIS#wrapLocal(ModelSetInstance) wrapLocal}'ed)
	 * @param value The value to use to satisfy the model value
	 */
	public static <M, MV extends M> void satisfyDynamicValue(String name, ModelInstanceType<M, MV> type, ModelSetInstance model, MV value)
		throws ModelException, QonfigEvaluationException, TypeConversionException {
		try { // Check for the value rigorously
			model.getModel().getValue(name, type);
		} catch (ModelException e) {
			throw new ModelException("No such dynamic model value: " + model.getModel().getIdentity() + "." + name);
		}
		ObservableModelSet.ModelComponentNode<?, ?> modelValue = model.getModel().getComponentIfExists(name);
		MV dynamicValue = (MV) model.get(modelValue);
		if (!(dynamicValue instanceof ModelType.HollowModelValue))
			throw new IllegalArgumentException("Dynamic model value " + model.getModel().getIdentity() + "." + name
				+ " is not a runtime model value. Satisfy with satisfyDynamicTypedValue(...).");
		ModelType.HollowModelValue<M, MV> hollow = (ModelType.HollowModelValue<M, MV>) dynamicValue;
		hollow.satisfy(value);
	}

	/**
	 * Checks if the given dynamic value is satisfied
	 *
	 * @param <M> The model type of the value
	 * @param <MV> The type of the value
	 * @param name The name of the value
	 * @param type The type of the value
	 * @param model The model instance the value may be satisfied in
	 * @return Whether the given dynamic value has already been satisfied with
	 *         {@link #satisfyDynamicValue(String, ModelInstanceType, ModelSetInstance, Object)}
	 * @throws ModelException
	 */
	public static <M, MV extends M> boolean isDynamicValueSatisfied(String name, ModelInstanceType<M, MV> type, ModelSetInstance model)
		throws ModelException, QonfigEvaluationException, TypeConversionException {
		try { // Check for the value rigorously
			model.getModel().getValue(name, type);
		} catch (ModelException e) {
			throw new ModelException("No such dynamic model value: " + model.getModel().getIdentity() + "." + name);
		}
		ObservableModelSet.ModelComponentNode<?, ?> modelValue = model.getModel().getComponentIfExists(name);
		MV dynamicValue = (MV) model.get(modelValue);
		if (!(dynamicValue instanceof ModelType.HollowModelValue))
			throw new IllegalArgumentException("Dynamic model value " + model.getModel().getIdentity() + "." + name
				+ " is not a runtime model value. Satisfy with satisfyDynamicTypedValue(...).");
		ModelType.HollowModelValue<M, MV> hollow = (ModelType.HollowModelValue<M, MV>) dynamicValue;
		return hollow.isSatisfied();
	}

	/**
	 * Called by some implementation to satisfy a metadata-declared dynamic (interpreted-value-specific) model value, if it is not already
	 * satisfied.
	 *
	 * @param <M> The model type of the value
	 * @param <MV> The type of the value
	 * @param name The name of the value
	 * @param type The type of the value
	 * @param model The model instance to satisfy the value in (should be {@link ExpressoQIS#wrapLocal(ModelSetInstance) wrapLocal}'ed)
	 * @param value The value to use to satisfy the model value
	 */
	public static <M, MV extends M> void satisfyDynamicValueIfUnsatisfied(String name, ModelInstanceType<M, MV> type,
		ModelSetInstance model, MV value) throws ModelException, QonfigEvaluationException, TypeConversionException {
		try { // Check for the value rigorously
			model.getModel().getValue(name, type);
		} catch (ModelException e) {
			throw new ModelException("No such dynamic model value: " + model.getModel().getIdentity() + "." + name);
		}
		ObservableModelSet.ModelComponentNode<?, ?> modelValue = model.getModel().getComponentIfExists(name);
		MV dynamicValue = (MV) model.get(modelValue);
		if (!(dynamicValue instanceof ModelType.HollowModelValue))
			throw new IllegalArgumentException("Dynamic model value " + model.getModel().getIdentity() + "." + name
				+ " is not a runtime model value. Satisfy with satisfyDynamicTypedValue(...).");
		ModelType.HollowModelValue<M, MV> hollow = (ModelType.HollowModelValue<M, MV>) dynamicValue;
		if (!hollow.isSatisfied())
			hollow.satisfy(value);
	}

	/**
	 * @param name The name of the dynamic model value
	 * @param models The model set that the dynamic model value is installed in
	 * @return The component node containing the {@link DynamicModelValue} with the given name
	 */
	public static ObservableModelSet.ModelComponentNode<?, ?> getDynamicValueComponent(String name, ObservableModelSet models) {
		ObservableModelSet.ModelComponentNode<?, ?> modelValue = models.getComponentIfExists(name);
		if (modelValue == null)
			throw new IllegalArgumentException("No such dynamic model value: " + models.getIdentity() + "." + name);
		else if (!(modelValue.getThing() instanceof DynamicTypedModelValueCreator))
			throw new IllegalArgumentException(models.getIdentity() + "." + name + " is not a dynamic model value");
		return modelValue;
	}

	/**
	 * For metadata-declared dynamic model values with no type specified, this method may be called to specify the type of the model,
	 * instead of specifying the value creator with {@link #satisfyDynamicValue(String, ObservableModelSet, ValueCreator)}. If this method
	 * is called, {@link #satisfyDynamicValue(String, ModelInstanceType, ModelSetInstance, Object)} must be called for the model instance
	 * set.
	 *
	 * @param <M> The model type of the value
	 * @param <MV> The type of the value
	 * @param name The name of the value
	 * @param models The model instance to satisfy the value's type in
	 * @param type The type for the dynamic model value
	 */
	public static <M, MV extends M> void satisfyDynamicValueType(String name, ObservableModelSet models, ModelInstanceType<M, MV> type) {
		ObservableModelSet.ModelComponentNode<?, ?> component = getDynamicValueComponent(name, models);
		satisfyDynamicValue(name, models,
			() -> new RuntimeModelValue<>(((DynamicTypedModelValueCreator<?, ?>) component.getThing()).getIdentity(), type));
	}

	/**
	 * Called by some implementation to satisfy a metadata-declared dynamic (interpreted-value-specific) model value.
	 *
	 * @param <M> The model type of the value
	 * @param <MV> The type of the value
	 * @param name The name of the value
	 * @param models The model instance to satisfy the value in
	 * @param satisfier The value creator to use to satisfy the model value
	 * @throws IllegalStateException If the value has already been satisfied for the given model set
	 */
	public static <M, MV extends M> void satisfyDynamicValue(String name, ObservableModelSet models, ValueCreator<M, MV> satisfier)
		throws IllegalStateException {
		DynamicTypedModelValueCreator.satisfyDynamicValue(name, models, satisfier, false);
	}

	/**
	 * Called by some implementation to satisfy a metadata-declared dynamic (interpreted-value-specific) model value, if it is not already
	 * satisfied.
	 *
	 * @param <M> The model type of the value
	 * @param <MV> The type of the value
	 * @param name The name of the value
	 * @param models The model instance to satisfy the value in
	 * @param satisfier The value creator to use to satisfy the model value
	 * @return Whether the value was newly satisfied as a result of this call
	 */
	public static <M, MV extends M> boolean satisfyDynamicValueIfUnsatisfied(String name, ObservableModelSet models,
		ValueCreator<M, MV> satisfier) {
		return DynamicTypedModelValueCreator.satisfyDynamicValue(name, models, satisfier, true);
	}

	/**
	 * A dynamic (interpreted-value-specific) model value
	 *
	 * @param <M> The model type of the value
	 * @param <MV> The type of the value
	 */
	public class RuntimeModelValue<M, MV extends M> implements DynamicModelValue<M, MV> {
		private final Identity theDeclaration;
		private final ModelInstanceType<M, MV> theType;

		/**
		 * Called by the Expresso interpreter to declare a dynamic (interpreted-value-specific) model value
		 *
		 * @param declaration The declared definition of the dynamic value
		 * @param type The type of the value
		 */
		public RuntimeModelValue(Identity declaration, ModelInstanceType<M, MV> type) {
			theDeclaration = declaration;
			theType = type;
		}

		@Override
		public Identity getDeclaration() {
			return theDeclaration;
		}

		@Override
		public ModelInstanceType<M, MV> getType() {
			return theType;
		}

		@Override
		public MV get(ModelSetInstance models) {
			return (MV) theType.getModelType().createHollowValue(getName(), theType);
		}

		@Override
		public MV forModelCopy(MV value, ModelSetInstance sourceModels, ModelSetInstance newModels) {
			HollowModelValue<M, MV> hollow = theType.getModelType().createHollowValue(getName(), theType);
			hollow.satisfy(value);
			return (MV) hollow;
		}

		@Override
		public BetterList<ValueContainer<?, ?>> getCores() {
			return BetterList.of(this);
		}

		@Override
		public int hashCode() {
			return Objects.hash(theDeclaration, theType);
		}

		@Override
		public boolean equals(Object obj) {
			if (obj == this)
				return true;
			else if (!(obj instanceof DynamicModelValue))
				return false;
			DynamicModelValue<?, ?> other = (DynamicModelValue<?, ?>) obj;
			if (!theDeclaration.equals(other.getDeclaration()))
				return false;
			try {
				return theType.equals(other.getType());
			} catch (QonfigEvaluationException e) {
				return false;
			}
		}

		@Override
		public String toString() {
			return theDeclaration.toString();
		}
	}

	/**
	 * A value container created by {@link DynamicTypedModelValueCreator} that wraps the dynamic value's
	 * {@link DynamicModelValue#satisfyDynamicValue(String, ObservableModelSet, ValueCreator) satisfier} (after satisfaction occurs)
	 *
	 * @param <M> The model type of the value
	 * @param <MV> The type of the value
	 */
	public class DynamicContainerWrapper<M, MV extends M> implements DynamicModelValue<M, MV> {
		private final DynamicTypedModelValueCreator<M, MV> theCreator;
		private ValueContainer<M, MV> theContainer;

		DynamicContainerWrapper(DynamicTypedModelValueCreator<M, MV> creator) {
			theCreator = creator;
		}

		@Override
		public ModelInstanceType<M, MV> getType() throws QonfigEvaluationException {
			if (theContainer == null)
				theContainer = theCreator.createDynamicContainer();
			return theContainer.getType();
		}

		@Override
		public MV get(ModelSetInstance models) throws QonfigEvaluationException {
			if (theContainer == null)
				theContainer = theCreator.createDynamicContainer();
			return theContainer.get(models);
		}

		@Override
		public MV forModelCopy(MV value, ModelSetInstance sourceModels, ModelSetInstance newModels) throws QonfigEvaluationException {
			if (theContainer == null)
				theContainer = theCreator.createDynamicContainer();
			return theContainer.forModelCopy(value, sourceModels, newModels);
		}

		@Override
		public BetterList<ValueContainer<?, ?>> getCores() {
			return BetterList.of(this);
		}

		@Override
		public String getName() {
			return theCreator.getName();
		}

		@Override
		public Identity getDeclaration() {
			return theCreator.getIdentity();
		}

		/** @return The type that this value's {@link #getCreator() creator} was declared with */
		public ModelInstanceType<M, MV> getDeclaredType() {
			return theCreator.getDeclaredType();
		}

		/** @return The dynamically-typed value creator that created this wrapper */
		public DynamicTypedModelValueCreator<M, MV> getCreator() {
			return theCreator;
		}
	}

	/**
	 * A dynamic-typed value whose type is not completely specified and must be
	 * {@link #satisfyDynamicValue(String, ObservableModelSet, ValueCreator) satisfied} before it is {@link ValueCreator#createContainer() used}
	 *
	 * @param <M> The model type of the value
	 * @param <MV> The type of the value, as far as it is known
	 */
	public class DynamicTypedModelValueCreator<M, MV extends M> implements Creator<M, MV>, Named {
		private final Identity theIdentity;
		private final ModelInstanceType<M, MV> theDeclaredType;

		private ValueCreator<M, MV> theSatisfier;

		/**
		 * @param declaration The declared definition of the dynamic value
		 * @param declaredType The type of the value, as far as it is known
		 */
		public DynamicTypedModelValueCreator(Identity declaration, ModelInstanceType<M, MV> declaredType) {
			theIdentity = declaration;
			theDeclaredType = declaredType;
		}

		@Override
		public String getName() {
			return theIdentity.getName();
		}

		/** @return The type that this value creator was declared with */
		public ModelInstanceType<M, MV> getDeclaredType() {
			return theDeclaredType;
		}

		@Override
		public Identity getIdentity() {
			return theIdentity;
		}

		@Override
		public DynamicModelValue<M, MV> createContainer() {
			return new DynamicContainerWrapper<>(this);
		}

		ValueContainer<M, MV> createDynamicContainer() throws QonfigEvaluationException {
			if (theSatisfier == null)
				theSatisfier = ObservableModelSet.IdentifableValueCreator.of(theIdentity,
					new DynamicModelValue.RuntimeModelValue<>(theIdentity, theDeclaredType));
			// throw new IllegalStateException("Dynamic model value " + getName() + " requested but not yet satisfied");
			ValueContainer<M, MV> container = theSatisfier.createContainer();
			if (!getDeclaredType().getModelType().equals(container.getType().getModelType()))
				throw new IllegalStateException(
					"Dynamic model value " + getName() + "(" + getDeclaredType() + ") satisfied with " + container.getType());
			for (int t = 0; t < getDeclaredType().getModelType().getTypeCount(); t++) {
				if (!TypeTokens.get().isAssignable(getDeclaredType().getType(t), container.getType().getType(t)))
					throw new IllegalStateException(
						"Dynamic model value " + getName() + "(" + getDeclaredType() + ") satisfied with " + container.getType());
			}
			return container;
		}

		static <M, MV extends M> boolean satisfyDynamicValue(String name, ObservableModelSet model, ValueCreator<M, MV> satisfier,
			boolean ignoreIfSatified) {
			ObservableModelSet.ModelComponentNode<?, ?> modelValue = getDynamicValueComponent(name, model);
			DynamicTypedModelValueCreator<M, MV> dmv = (DynamicTypedModelValueCreator<M, MV>) modelValue.getThing();
			if (dmv.theSatisfier == null) {
				dmv.theSatisfier = satisfier;
				return true;
			} else if (ignoreIfSatified || dmv.theSatisfier == satisfier)
				return false;
			else
				throw new IllegalStateException("Dynamic model value " + name + " has already been satisfied");
		}

		@Override
		public String toString() {
			if (theDeclaredType != null)
				return getName() + "(" + theDeclaredType + ")";
			else
				return getName();
		}
	}

	/** Implementation details in this interface */
	class Impl {
		private static class ElementModelData {
			Set<QonfigElementOrAddOn> types = new HashSet<>();
			QonfigElementOrAddOn withElementModel;
			QonfigChildDef elementModel;
			QonfigChildDef modelValue;
			QonfigAttributeDef nameAttr;
			QonfigAttributeDef nameAttrAttr;
			QonfigValueType identifierType;
			QonfigAttributeDef typeAttr;
			QonfigAttributeDef sourceAttr;
		}

		private static ConcurrentHashMap<QonfigElementOrAddOn, Map<String, Identity>> DYNAMIC_VALUES = new ConcurrentHashMap<>();

		static Map<String, Identity> getDynamicValues(QonfigToolkit expresso, ElementModelData modelData, QonfigElementOrAddOn type,
			Map<String, Identity> values) {
			if (modelData == null) {
				modelData = new ElementModelData();
				modelData.withElementModel = expresso.getElementOrAddOn("with-element-model");
			}
			if (!modelData.types.add(type) || !modelData.withElementModel.isAssignableFrom(type)) {
				return values == null ? Collections.emptyMap() : values;
			}
			Map<String, Identity> found = DYNAMIC_VALUES.get(type);
			if (found == null) {
				synchronized (Impl.class) {
					found = DYNAMIC_VALUES.get(type);
					if (found == null) {
						found = compileDynamicValues(expresso, modelData, type);
						DYNAMIC_VALUES.put(type, found);
					}
				}
			}
			if (values == null)
				values = new LinkedHashMap<>();
			values.putAll(found);
			return values;
		}

		private static Map<String, Identity> compileDynamicValues(QonfigToolkit expresso, ElementModelData modelData,
			QonfigElementOrAddOn type) {
			Map<String, Identity> values = new LinkedHashMap<>();
			if (type.getSuperElement() != null)
				getDynamicValues(expresso, modelData, type.getSuperElement(), values);
			for (QonfigAddOn inh : type.getInheritance())
				getDynamicValues(expresso, modelData, inh, values);
			if (modelData.elementModel == null)
				modelData.elementModel = expresso.getMetaChild("with-element-model", "element-model");
			QonfigElement metadata = type.getMetadata().getRoot().getChildrenByRole().get(modelData.elementModel.getDeclared()).peekFirst();
			if (metadata != null) {
				if (modelData.modelValue == null) {
					modelData.modelValue = expresso.getChild("element-model", "value");
					modelData.nameAttr = expresso.getAttribute("named", "name");
					modelData.nameAttrAttr = expresso.getAttribute("element-model-value", "name-attribute");
					modelData.identifierType = expresso.getAttributeType("identifier");
					modelData.typeAttr = expresso.getAttribute("typed", "type");
					modelData.sourceAttr = expresso.getAttribute("element-model-value", "source-attribute");
				}
				for (QonfigElement value : metadata.getChildrenByRole().get(modelData.modelValue.getDeclared())) {
					String name = value.getAttributeText(modelData.nameAttr);
					String nameAttrS = value.getAttributeText(modelData.nameAttrAttr);
					QonfigAttributeDef nameAttr;
					if (nameAttrS != null) {
						if (!"$".equals(name))
							throw new IllegalArgumentException("Cannot specify both name and name-attribute on an internal model value");
						name = null;
						nameAttr = type.getAttribute(nameAttrS);
						if (nameAttr == null)
							throw new IllegalArgumentException("No such attribute " + type + "." + nameAttrS);
						else if (nameAttr.getType() != modelData.identifierType)
							throw new IllegalArgumentException(
								"name-attribute must refer to an attribute of type 'identifier', not " + nameAttr.getType());
						else if (nameAttr.getSpecification() != SpecificationType.Required && nameAttr.getDefaultValue() == null)
							throw new IllegalArgumentException(
								"name-attribute " + nameAttr + " must either be required or specify a default");
					} else {
						nameAttr = null;
					}
					String typeName = value.getAttributeText(modelData.typeAttr);
					String sourceName = value.getAttributeText(modelData.sourceAttr);
					boolean sourceValue;
					QonfigAttributeDef sourceAttr;
					if (sourceName == null) {
						sourceValue = false;
						sourceAttr = null;
					} else if (sourceName.isEmpty()) {
						sourceValue = true;
						sourceAttr = null;
					} else {
						sourceValue = false;
						int dot = sourceName.indexOf('.');
						if (dot >= 0)
							sourceAttr = type.getDeclarer().getAttribute(//
								sourceName.substring(0, dot), sourceName.substring(dot + 1));
						else
							sourceAttr = type.getAttribute(sourceName);
						if (sourceAttr == null)
							throw new IllegalArgumentException(
								"For dynamic value " + type + "." + name + " , no such source attribute found: " + sourceName);
					}
					Identity newModelValue = new Identity(type, name, nameAttr, typeName, sourceAttr, sourceValue, value);
					Identity overridden = values.get(newModelValue.getName());
					if (overridden != null)
						throw new IllegalArgumentException("Type " + type + " declares a dynamic value '" + newModelValue.getName()
						+ "' that clashes with the value of the same name declared by " + overridden.getOwner());
					values.put(newModelValue.getName(), newModelValue);
				}
			}
			return Collections.unmodifiableMap(values);
		}
	}
}