package org.observe.expresso.qonfig;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.observe.expresso.ExpressoEnv;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ModelException;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelType;
import org.observe.expresso.ModelType.HollowModelValue;
import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.expresso.ObservableModelSet;
import org.observe.expresso.ObservableModelSet.CompiledModelValue;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ModelValueSynth;
import org.observe.expresso.TypeConversionException;
import org.observe.util.TypeTokens;
import org.qommons.Named;
import org.qommons.collect.BetterList;
import org.qommons.config.QonfigAddOn;
import org.qommons.config.QonfigAttributeDef;
import org.qommons.config.QonfigChildDef;
import org.qommons.config.QonfigElement;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigToolkit;
import org.qommons.config.QonfigValueType;
import org.qommons.config.SpecificationType;
import org.qommons.ex.ExSupplier;
import org.qommons.io.ErrorReporting;
import org.qommons.io.LocatedFilePosition;

import com.google.common.reflect.TypeToken;

/**
 * A dynamic (interpreted-value-specific) model value
 *
 * @param <M> The model type of the value
 * @param <MV> The type of the value
 */
public interface DynamicModelValue<M, MV extends M> extends ModelValueSynth<M, MV>, Named {
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
			StringBuilder str = new StringBuilder(theOwner.toString()).append('.');
			if (theName != null)
				str.append(theName);
			else
				str.append('{').append(theNameAttribute.getName()).append('}');
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
	 * Dynamic values in models should always be
	 * {@link org.observe.expresso.ObservableModelSet.Builder#withMaker(String, CompiledModelValue, LocatedFilePosition) added} as an
	 * instance of this type.
	 *
	 * @param <M> The model type of the dynamic value
	 * @param <MV> The type of the dynamic value
	 */
	public interface Compiled<M, MV extends M> extends ObservableModelSet.IdentifableCompiledValue<M, MV> {
		@Override
		Identity getIdentity();
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
	 * @param model The model instance to satisfy the value in (should be {@link ExpressoEnv#wrapLocal(ModelSetInstance) wrapLocal}'ed)
	 * @param value The value to use to satisfy the model value
	 * @throws ModelException If the given model value does not exist
	 * @throws ModelInstantiationException If the value cannot be instantiated
	 * @throws TypeConversionException If the value does not match the specified type
	 */
	public static <M, MV extends M> void satisfyDynamicValue(String name, ModelInstanceType<M, MV> type, ModelSetInstance model, MV value)
		throws ModelException, ModelInstantiationException, TypeConversionException {
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
	 * @throws ModelException If the given model value does not exist
	 * @throws ModelInstantiationException If the value cannot be instantiated
	 * @throws TypeConversionException If the value does not match the specified type
	 */
	public static <M, MV extends M> boolean isDynamicValueSatisfied(String name, ModelInstanceType<M, MV> type, ModelSetInstance model)
		throws ModelException, ModelInstantiationException, TypeConversionException {
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
	 * @param model The model instance to satisfy the value in (should be {@link ExpressoEnv#wrapLocal(ModelSetInstance) wrapLocal}'ed)
	 * @param value The value to use to satisfy the model value
	 * @throws ModelException If the given model value does not exist
	 * @throws ModelInstantiationException If the value cannot be instantiated
	 * @throws TypeConversionException If the value does not match the specified type
	 */
	public static <M, MV extends M> void satisfyDynamicValueIfUnsatisfied(String name, ModelInstanceType<M, MV> type,
		ModelSetInstance model, MV value)
			throws ModelException, ModelInstantiationException, TypeConversionException {
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
	 * instead of specifying the value creator with {@link #satisfyDynamicValue(String, ObservableModelSet, CompiledModelValue)}. If this method
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
		class TypeSatisfiedDynamicCompiledValue implements CompiledModelValue<M, MV> {
			ModelInstanceType<M, MV> getType() {
				return type;
			}

			@Override
			public ModelType<M> getModelType() {
				return type.getModelType();
			}

			@Override
			public ModelValueSynth<M, MV> createSynthesizer() throws ExpressoInterpretationException {
				return new RuntimeModelValue<>(((DynamicTypedModelValueCreator<?, ?>) component.getThing()).getIdentity(), type);
			}

			@Override
			public int hashCode() {
				return type.hashCode();
			}

			@Override
			public boolean equals(Object obj) {
				return obj instanceof TypeSatisfiedDynamicCompiledValue && type.equals(((TypeSatisfiedDynamicCompiledValue) obj).getType());
			}

			@Override
			public String toString() {
				return "Dynamic " + type;
			}
		}
		satisfyDynamicValue(name, models, new TypeSatisfiedDynamicCompiledValue());
	}

	/**
	 *
	 * @param <M> The model type of the value
	 * @param <MV> The type of the value
	 * @param name The name of the value
	 * @param models The models to satisfy the value's type in
	 * @param modelType The model type to satisfy the value as
	 * @param satisfier A function to satisfy the dynamic value each time it is evaluated
	 * @throws IllegalStateException If the value has already been satisfied
	 */
	public static <M, MV extends M> void satisfyDynamicValue(String name, ObservableModelSet models, ModelType<M> modelType,
		ExSupplier<ModelValueSynth<M, MV>, ExpressoInterpretationException> satisfier) throws IllegalStateException {
		DynamicTypedModelValueCreator.satisfyDynamicValue(name, models, CompiledModelValue.of(name, modelType, satisfier), false);
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
	public static <M, MV extends M> void satisfyDynamicValue(String name, ObservableModelSet models, CompiledModelValue<M, MV> satisfier)
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
		CompiledModelValue<M, MV> satisfier) {
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
		public ModelType<M> getModelType() {
			return theType.getModelType();
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
		public List<? extends ModelValueSynth<?, ?>> getComponents() {
			return Collections.emptyList();
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
			} catch (ExpressoInterpretationException e) {
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
	 * {@link DynamicModelValue#satisfyDynamicValue(String, ObservableModelSet, CompiledModelValue) satisfier} (after satisfaction occurs)
	 *
	 * @param <M> The model type of the value
	 * @param <MV> The type of the value
	 */
	public class DynamicContainerWrapper<M, MV extends M> implements DynamicModelValue<M, MV> {
		private final DynamicTypedModelValueCreator<M, MV> theCreator;
		private ModelValueSynth<M, MV> theContainer;

		DynamicContainerWrapper(DynamicTypedModelValueCreator<M, MV> creator) {
			theCreator = creator;
		}

		@Override
		public ModelType<M> getModelType() {
			return theCreator.getModelType();
		}

		@Override
		public ModelInstanceType<M, MV> getType() throws ExpressoInterpretationException {
			if (theContainer == null)
				theContainer = theCreator.createDynamicContainer();
			return theContainer.getType();
		}

		/**
		 * @return Creates or retrieves the value container from this wrapper
		 * @throws ModelInstantiationException If the model value could not be instantiated
		 */
		protected ModelValueSynth<M, MV> getContainer() throws ModelInstantiationException {
			if (theContainer == null) {
				try {
					theContainer = theCreator.createDynamicContainer();
				} catch (ExpressoInterpretationException e) {
					throw new ModelInstantiationException(e.getPosition(), e.getErrorLength(), e);
				}
			}
			return theContainer;
		}

		@Override
		public MV get(ModelSetInstance models) throws ModelInstantiationException {
			return getContainer().get(models);
		}

		@Override
		public MV forModelCopy(MV value, ModelSetInstance sourceModels, ModelSetInstance newModels) throws ModelInstantiationException {
			return getContainer().forModelCopy(value, sourceModels, newModels);
		}

		@Override
		public BetterList<ModelValueSynth<?, ?>> getCores() {
			return BetterList.of(this); // Don't remember why I did this, but I'm leaving it alone until it causes problems
		}

		@Override
		public List<? extends ModelValueSynth<?, ?>> getComponents() {
			try {
				return Collections.singletonList(getContainer());
			} catch (ModelInstantiationException e) {
				throw new IllegalStateException("Could not synthesize dynamic value", e);
			}
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
	 * {@link #satisfyDynamicValue(String, ObservableModelSet, CompiledModelValue) satisfied} before it is {@link CompiledModelValue#createSynthesizer() used}
	 *
	 * @param <M> The model type of the value
	 * @param <MV> The type of the value, as far as it is known
	 */
	public class DynamicTypedModelValueCreator<M, MV extends M> implements Compiled<M, MV>, Named {
		private final Identity theIdentity;
		private final ModelType<M> theModelType;
		private final ErrorReporting theReporting;
		private final ExSupplier<ModelInstanceType<M, MV>, ExpressoInterpretationException> theTypeGetter;
		private ModelInstanceType<M, MV> theDeclaredType;

		private CompiledModelValue<M, MV> theSatisfier;

		/**
		 * @param declaration The declared definition of the dynamic value
		 * @param modelType The model type of the dynamic value
		 * @param reporting The error reporting for if this dynamic value's type is not satisfied in time
		 * @param declaredType Supplies the type of the value, as far as it is known
		 */
		public DynamicTypedModelValueCreator(Identity declaration, ModelType<M> modelType, ErrorReporting reporting,
			ExSupplier<ModelInstanceType<M, MV>, ExpressoInterpretationException> declaredType) {
			theIdentity = declaration;
			theModelType = modelType;
			theReporting = reporting;
			theTypeGetter = declaredType;
		}

		@Override
		public String getName() {
			return theIdentity.getName();
		}

		@Override
		public ModelType<M> getModelType() {
			return theModelType;
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
		public DynamicModelValue<M, MV> createSynthesizer() {
			return new DynamicContainerWrapper<>(this);
		}

		ModelValueSynth<M, MV> createDynamicContainer() throws ExpressoInterpretationException {
			if (theDeclaredType == null)
				theDeclaredType = theTypeGetter.get();
			if (theSatisfier == null)
				theSatisfier = ObservableModelSet.IdentifableCompiledValue.of(theIdentity,
					new DynamicModelValue.RuntimeModelValue<>(theIdentity, theDeclaredType));
			ModelValueSynth<M, MV> container = theSatisfier.createSynthesizer();
			if (!getDeclaredType().getModelType().equals(container.getType().getModelType()))
				throw new IllegalStateException(
					"Dynamic model value '" + getName() + "' (" + getDeclaredType() + ") satisfied with " + container.getType());
			boolean wildcard = false;
			for (int t = 0; t < getDeclaredType().getModelType().getTypeCount(); t++) {
				TypeToken<?> pt = container.getType().getType(t);
				if (!TypeTokens.get().isAssignable(getDeclaredType().getType(t), pt))
					throw new IllegalStateException(
						"Dynamic model value '" + getName() + "' (" + getDeclaredType() + ") satisfied with " + container.getType());
				else if (TypeTokens.get().isTrivialType(pt.getType()))
					wildcard = true;
			}
			if (wildcard)
				theReporting
				.error("Type not specified for dynamic model value '" + getName() + "' (" + theDeclaredType + ") before being needed");
			return container;
		}

		static <M, MV extends M> boolean satisfyDynamicValue(String name, ObservableModelSet model, CompiledModelValue<M, MV> satisfier,
			boolean ignoreIfSatified) {
			ObservableModelSet.ModelComponentNode<?, ?> modelValue = getDynamicValueComponent(name, model);
			DynamicTypedModelValueCreator<M, MV> dmv = (DynamicTypedModelValueCreator<M, MV>) modelValue.getThing();
			if (dmv.theSatisfier == null) {
				dmv.theSatisfier = satisfier;
				return true;
			} else if (ignoreIfSatified || dmv.theSatisfier.equals(satisfier))
				return false;
			else
				throw new IllegalStateException("Dynamic model value '" + name + "' has already been satisfied");
		}

		@Override
		public String toString() {
			if (theDeclaredType != null)
				return getName() + "(" + theDeclaredType + ")";
			else
				return getName();
		}
	}

	/** A cache of dynamic model values for a document */
	public class Cache {
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

		private ConcurrentHashMap<QonfigElementOrAddOn, Map<String, Identity>> theDynamicValues = new ConcurrentHashMap<>();

		/**
		 * @param expresso The toolkit to get expresso types from
		 * @param type The element type to get the dynamic values for
		 * @param values The map to add the dynamic values into
		 * @return The identities/definitions of all dynamic values defined on the given type, grouped by name/name attribute
		 */
		public Map<String, Identity> getDynamicValues(QonfigToolkit expresso, QonfigElementOrAddOn type,
			Map<String, Identity> values) {
			return getDynamicValues(expresso, null, type, values);
		}

		Map<String, Identity> getDynamicValues(QonfigToolkit expresso, ElementModelData modelData, QonfigElementOrAddOn type,
			Map<String, Identity> values) {
			if (modelData == null) {
				modelData = new ElementModelData();
				modelData.withElementModel = expresso.getElementOrAddOn("with-element-model");
			}
			if (!modelData.types.add(type) || !modelData.withElementModel.isAssignableFrom(type)) {
				return values == null ? Collections.emptyMap() : values;
			}
			Map<String, Identity> found = theDynamicValues.get(type);
			if (found == null) {
				synchronized (Cache.class) {
					found = theDynamicValues.get(type);
					if (found == null) {
						found = compileDynamicValues(expresso, modelData, type);
						theDynamicValues.put(type, found);
					}
				}
			}
			if (values == null)
				values = new LinkedHashMap<>();
			values.putAll(found);
			return values;
		}

		private Map<String, Identity> compileDynamicValues(QonfigToolkit expresso, ElementModelData modelData,
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