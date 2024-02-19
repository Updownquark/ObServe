package org.observe.expresso.qonfig;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.observe.expresso.CompiledExpressoEnv;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.InterpretedExpressoEnv;
import org.observe.expresso.ModelException;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelType;
import org.observe.expresso.ModelType.ModelInstanceConverter;
import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.expresso.ObservableModelSet;
import org.observe.expresso.ObservableModelSet.Builder;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelComponentId;
import org.observe.expresso.ObservableModelSet.ModelComponentNode;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.qommons.config.QonfigAddOn;
import org.qommons.config.QonfigInterpretationException;

/**
 * An add on containing an &lt;ext-model> child that may specify external model values to be required in the environment of content
 * referring to the structure interpreted from the add-on's target element
 */
public class ExWithRequiredModels extends ExFlexibleElementModelAddOn<ExElement> {
	/** The XML name of this add-on */
	public static final String WITH_REQUIRED_MODELS = "with-required-models";

	/** {@link ExWithRequiredModels} definition */
	@ExElementTraceable(toolkit = ExpressoSessionImplV0_1.CORE,
		qonfigType = WITH_REQUIRED_MODELS,
		interpretation = Interpreted.class,
		instance = ExWithRequiredModels.class)
	public static class Def extends ExFlexibleElementModelAddOn.Def<ExElement, ExWithRequiredModels> {
		private ObservableModelElement.ExtModelElement.Def<?> theRequiredModelElement;

		/**
		 * @param type The Qonfig type of this add-on
		 * @param element The element affected by this add-on
		 */
		public Def(QonfigAddOn type, ExElement.Def<? extends ExElement> element) {
			super(type, element);
		}

		/** @return The &lt;ext-model> element specifying the external model values required by content referring to the element */
		@QonfigChildGetter("required")
		public ObservableModelElement.ExtModelElement.Def<?> getRequiredModelElement() {
			return theRequiredModelElement;
		}

		@Override
		public void update(ExpressoQIS session, ExElement.Def<?> element) throws QonfigInterpretationException {
			super.update(session, element);
			if (session.children().get("required").get().isEmpty()) { // Don't create a required model if there's no reason to
				theRequiredModelElement = null;
				return;
			}
			session.put(ExtModelValueElement.EXT_MODEL_VALUE_HANDLER, new ExtModelValueElement.ExtModelValueHandler() {
				@Override
				public <M> void handleExtValue(ExtModelValueElement.Def<M> value, Builder builder, ExpressoQIS valueSession)
					throws QonfigInterpretationException {
					String name = value.getAddOn(ExNamed.Def.class).getName();
					PlaceholderExtValue<?> placeholder = new PlaceholderExtValue<>(name, value);
					placeholder.setModelId(addElementValue(name, placeholder, builder, value.getFilePosition()).getIdentity());
				}
			});
			if (!session.children().get("required").get().isEmpty())
				createBuilder(session);
			theRequiredModelElement = getElement().syncChild(ObservableModelElement.ExtModelElement.Def.class, theRequiredModelElement,
				session, "required");
			if (theRequiredModelElement != null)
				getElement().setExpressoEnv(getElement().getExpressoEnv().with(theRequiredModelElement.getExpressoEnv().getModels()));
			session.setExpressoEnv(getElement().getExpressoEnv());
		}

		/**
		 * @param contextEnv The expresso environment of content referring to this add-on's target element
		 * @return The required model context mapping model values available in the calling context to model values required by this add-on
		 * @throws QonfigInterpretationException If any required model values are not present in the calling environment
		 */
		public RequiredModelContext getContext(CompiledExpressoEnv contextEnv) throws QonfigInterpretationException {
			if (theRequiredModelElement == null)
				return null;
			Map<ModelComponentId, ModelComponentId> contextValues = new LinkedHashMap<>();
			populateContextValues(contextValues, theRequiredModelElement, contextEnv);
			return new RequiredModelContext(contextValues);
		}

		private void populateContextValues(Map<ModelComponentId, ModelComponentId> contextValues,
			ObservableModelElement.ExtModelElement.Def<?> required, CompiledExpressoEnv env) throws QonfigInterpretationException {
			for (ExtModelValueElement.Def<?> value : required.getValues()) {
				ModelComponentNode<?> component;
				try {
					component = env.getModels().getComponent(value.getModelPath());
				} catch (ModelException e) {
					throw new QonfigInterpretationException("Model value '" + value.getModelPath() + "' is required to apply "
						+ getElement() + ", but is not present in this model", env.reporting().getPosition(), 0);
				}
				contextValues.put(((PlaceholderExtValue<?>) super.getElementValue(value.getModelPath())).getModelId(),
					component.getIdentity());
			}
			for (ObservableModelElement.ExtModelElement.Def<?> subModel : required.getSubModels())
				populateContextValues(contextValues, subModel, env);
		}

		@Override
		public Interpreted interpret(ExElement.Interpreted<? extends ExElement> element) {
			return new Interpreted(this, element);
		}
	}

	/** {@link ExWithRequiredModels} interpretation */
	public static class Interpreted extends ExFlexibleElementModelAddOn.Interpreted<ExElement, ExWithRequiredModels> {
		private ObservableModelElement.ExtModelElement.Interpreted<?> theRequiredModelElement;

		/**
		 * @param definition The definition to interpret
		 * @param element The element affected by this add-on
		 */
		protected Interpreted(Def definition, ExElement.Interpreted<? extends ExElement> element) {
			super(definition, element);
		}

		/** @return The &lt;ext-model> element specifying the external model values required by content referring to the element */
		public ObservableModelElement.ExtModelElement.Interpreted<?> getRequiredModelElement() {
			return theRequiredModelElement;
		}

		@Override
		public Def getDefinition() {
			return (Def) super.getDefinition();
		}

		@Override
		public void update(ExElement.Interpreted<?> element) throws ExpressoInterpretationException {
			super.update(element);
			theRequiredModelElement = getElement().syncChild(getDefinition().getRequiredModelElement(), theRequiredModelElement,
				def -> def.interpret(getElement()), (el, elEnv) -> el.update(elEnv));
		}

		@Override
		public Class<ExWithRequiredModels> getInstanceType() {
			return ExWithRequiredModels.class;
		}

		/**
		 * @param context The compiled model context generated by {@link Def#getContext(CompiledExpressoEnv)} for the calling environment
		 * @param contextEnv The expresso environment of the calling environment to interpret expressions
		 * @return The interpreted required model context capable of populating values required by this add-on using values from the calling
		 *         environment in a model instance
		 * @throws ExpressoInterpretationException If any calling model values could not be interpreted to satisfy values in this add-on
		 */
		public InterpretedRequiredModelContext getContextConverter(RequiredModelContext context, InterpretedExpressoEnv contextEnv)
			throws ExpressoInterpretationException {
			if (getDefinition().getRequiredModelElement() == null || context == null)
				return null;
			Map<ModelComponentId, ContextValueConverter<?>> contextValues = new LinkedHashMap<>();
			populateContextValues(contextValues, getDefinition().getRequiredModelElement(), context.getContextValues(), contextEnv);
			return new InterpretedRequiredModelContext(contextValues);
		}

		private void populateContextValues(Map<ModelComponentId, ContextValueConverter<?>> contextValues,
			ObservableModelElement.ExtModelElement.Def<?> required, Map<ModelComponentId, ModelComponentId> compiledContextValues,
			InterpretedExpressoEnv contextEnv) throws ExpressoInterpretationException {
			for (ExtModelValueElement.Def<?> value : required.getValues()) {
				ModelInstanceType<?, ?> type = value.getType(contextEnv);
				ModelComponentId internalValue = getElementValue(value.getModelPath()).getIdentity();
				ModelComponentId contextValue = compiledContextValues.get(internalValue);
				contextValues.put(contextValue, new ContextValueConverter<>(internalValue,
					contextEnv.getModels().getComponent(contextValue).interpret(contextEnv).getType().convert(type, contextEnv)));
			}
			for (ObservableModelElement.ExtModelElement.Def<?> subModel : required.getSubModels())
				populateContextValues(contextValues, subModel, compiledContextValues, contextEnv);
		}

		@Override
		public ExWithRequiredModels create(ExElement element) {
			return new ExWithRequiredModels(element);
		}
	}

	private ObservableModelElement.ExtModelElement theExtModelElement;

	/** @param element The element this add-on affects */
	protected ExWithRequiredModels(ExElement element) {
		super(element);
	}

	@Override
	public Class<Interpreted> getInterpretationType() {
		return (Class<Interpreted>) (Class<?>) Interpreted.class;
	}

	/** @return The &lt;ext-model> element specifying the external model values required by content referring to the element */
	public ObservableModelElement.ExtModelElement getRequiredModelElement() {
		return theExtModelElement;
	}

	@Override
	public void update(ExAddOn.Interpreted<?, ?> interpreted, ExElement element) throws ModelInstantiationException {
		super.update(interpreted, element);

		Interpreted myInterpreted = (Interpreted) interpreted;
		if (myInterpreted.getRequiredModelElement() == null)
			theExtModelElement = null;
		else if (theExtModelElement == null
			|| theExtModelElement.getIdentity() != myInterpreted.getRequiredModelElement().getDefinition().getIdentity())
			theExtModelElement = myInterpreted.getRequiredModelElement().create(getElement());
		if (theExtModelElement != null)
			theExtModelElement.update(myInterpreted.getRequiredModelElement(), getElement());
	}

	@Override
	public void instantiated() throws ModelInstantiationException {
		super.instantiated();
		if (theExtModelElement != null)
			theExtModelElement.instantiated();
	}

	@Override
	public void instantiate(ModelSetInstance models) throws ModelInstantiationException {
		super.instantiate(models);
		if (theExtModelElement != null)
			theExtModelElement.instantiate(models);
	}

	static class PlaceholderExtValue<M> extends ExFlexibleElementModelAddOn.PlaceholderModelValue<M> {
		private final ExtModelValueElement.Def<M> theSpec;
		private ModelComponentId theModelId;

		PlaceholderExtValue(String name, ExtModelValueElement.Def<M> spec) {
			super(name);
			theSpec = spec;
		}

		@Override
		public ModelType<M> getModelType() {
			return theSpec.getModelType();
		}

		public ModelComponentId getModelId() {
			return theModelId;
		}

		public void setModelId(ModelComponentId modelId) {
			theModelId = modelId;
		}

		@Override
		public InterpretedValueSynth<M, ?> interpret(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
			return create(env, theSpec.getType(env));
		}
	}

	/** Compiled context structure mapping values from a calling model context to values in a required model context */
	public static class RequiredModelContext {
		private final Map<ModelComponentId, ModelComponentId> theContextValues;

		RequiredModelContext(Map<ModelComponentId, ModelComponentId> contextValues) {
			theContextValues = contextValues;
		}

		/**
		 * @param inherited The inherited model set
		 * @return The required model context of the inherited model
		 */
		public RequiredModelContext forInherited(ObservableModelSet inherited) {
			Map<ModelComponentId, ModelComponentId> inhCtxValues = null;
			for (Map.Entry<ModelComponentId, ModelComponentId> ctxValue : theContextValues.entrySet()) {
				ModelComponentId inhComponent;
				try {
					inhComponent = inherited.getComponent(ctxValue.getValue().getName()).getIdentity();
				} catch (ModelException e) {
					throw new IllegalArgumentException(
						"Given models (" + inherited.getIdentity() + ") does not inherit source model " + ctxValue.getKey().getRootId(), e);
				}
				if (inhComponent != ctxValue.getValue()) {
					if (inhCtxValues == null) {
						inhCtxValues = new LinkedHashMap<>(theContextValues.size() * 3 / 2 + 1);
						inhCtxValues.putAll(theContextValues);
					}
					inhCtxValues.put(ctxValue.getKey(), inhComponent);
				}
			}
			if (inhCtxValues == null)
				return this;
			return new RequiredModelContext(inhCtxValues);
		}

		/** @return The mapping of the required model values to values of the same name specified in the calling context */
		public Map<ModelComponentId, ModelComponentId> getContextValues() {
			return Collections.unmodifiableMap(theContextValues);
		}

		/**
		 * @param other The other required model context to join
		 * @return A required model context containing mappings for two different &lt;with-required-models>-tagged elements
		 */
		public RequiredModelContext and(RequiredModelContext other) {
			if (other == null)
				return this;
			Map<ModelComponentId, ModelComponentId> combo = new LinkedHashMap<>();
			combo.putAll(theContextValues);
			combo.putAll(other.getContextValues());
			return new RequiredModelContext(combo);
		}
	}

	/**
	 * Interpreted context structure capable of populating into a {@link ModelSetInstance} values required by a &lt;-with-required-models>
	 * -tagged element from values of the same name in a calling model context
	 */
	public static class InterpretedRequiredModelContext {
		private final Map<ModelComponentId, ContextValueConverter<?>> theContextValues;

		InterpretedRequiredModelContext(Map<ModelComponentId, ContextValueConverter<?>> contextValues) {
			theContextValues = contextValues;
		}

		/** @return The model ID of the required model */
		public ModelComponentId getModel() {
			return theContextValues.values().iterator().next().theInternalValue.getRootId();
		}

		/**
		 * @param other The other required model context to join
		 * @return A required model context for populating a model instance with values for two different &lt;with-required-models>-tagged
		 *         elements
		 */
		public InterpretedRequiredModelContext and(InterpretedRequiredModelContext other) {
			if (other == null)
				return this;
			Map<ModelComponentId, ContextValueConverter<?>> combo = new LinkedHashMap<>();
			combo.putAll(theContextValues);
			combo.putAll(other.theContextValues);
			return new InterpretedRequiredModelContext(combo);
		}

		/**
		 * @param model The model instance to populate with required model values
		 * @throws ModelInstantiationException If any of the required values could not be instantiated
		 */
		public void populateModel(ModelSetInstance model) throws ModelInstantiationException {
			for (Map.Entry<ModelComponentId, ContextValueConverter<?>> contextValue : theContextValues.entrySet())
				installInternalValue(model, contextValue.getKey(), contextValue.getValue());
		}

		private <M> void installInternalValue(ModelSetInstance model, ModelComponentId externalValue, ContextValueConverter<M> value)
			throws ModelInstantiationException {
			value.installInternalValue(model, (M) model.get(externalValue));
		}
	}

	private static class ContextValueConverter<M> {
		private final ModelComponentId theInternalValue;
		private final ModelInstanceConverter<M, ?> theConverter;

		ContextValueConverter(ModelComponentId internalValue, ModelInstanceConverter<M, ?> converter) {
			theInternalValue = internalValue;
			theConverter = converter;
		}

		void installInternalValue(ModelSetInstance model, M externalValue) throws ModelInstantiationException {
			// if (!ExFlexibleElementModelAddOn.isElementValueSatisfied(theInternalValue, model)) {
			ExFlexibleElementModelAddOn.satisfyElementValue(theInternalValue, model, //
				theConverter == null ? externalValue : theConverter.convert(externalValue));
			// }
		}
	}
}
