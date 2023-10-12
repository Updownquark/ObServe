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
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelComponentId;
import org.observe.expresso.ObservableModelSet.ModelComponentNode;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.qommons.config.QonfigAddOn;
import org.qommons.config.QonfigInterpretationException;

public class ExWithRequiredModels extends ExFlexibleElementModelAddOn<ExElement> {
	@ExElementTraceable(toolkit = ExpressoSessionImplV0_1.CORE,
		qonfigType = "with-required-models",
		interpretation = Interpreted.class,
		instance = ExWithRequiredModels.class)
	public static class Def extends ExFlexibleElementModelAddOn.Def<ExElement, ExWithRequiredModels> {
		private ObservableModelElement.ExtModelElement.Def<?> theRequiredModelElement;

		public Def(QonfigAddOn type, ExElement.Def<? extends ExElement> element) {
			super(type, element);
		}

		@QonfigChildGetter("required")
		public ObservableModelElement.ExtModelElement.Def<?> getRequiredModelElement() {
			return theRequiredModelElement;
		}

		@Override
		public void update(ExpressoQIS session, ExElement.Def<? extends ExElement> element) throws QonfigInterpretationException {
			super.update(session, element);
			if (session.getChildren("required").isEmpty()) { // Don't create a required model if there's no reason to
				theRequiredModelElement = null;
				return;
			}
			session.put(ObservableModelElement.PREVENT_MODEL_BUILDING, false);
			ObservableModelSet.Builder builder = createBuilder(session);
			theRequiredModelElement = ExElement.useOrReplace(ObservableModelElement.ExtModelElement.Def.class, theRequiredModelElement,
				session, "required");
			installModelValues(session, builder, theRequiredModelElement);
			session.setModels(theRequiredModelElement.getExpressoEnv().getModels());
			getElement().setExpressoEnv(getElement().getExpressoEnv().with(theRequiredModelElement.getExpressoEnv().getModels()));
		}

		private void installModelValues(ExpressoQIS session, ObservableModelSet.Builder builder,
			ObservableModelElement.ExtModelElement.Def<?> required) throws QonfigInterpretationException {
			for (ExtModelValueElement.Def<?> value : required.getValues()) {
				String name = value.getAddOn(ExNamed.Def.class).getName();
				PlaceholderExtValue<?> placeholder = new PlaceholderExtValue<>(name, value);
				addElementValue(name, placeholder, builder, value.getFilePosition());
			}
			for (ObservableModelElement.ExtModelElement.Def<?> subModel : required.getSubModels())
				installModelValues(session, builder.createSubModel(subModel.getName(), subModel.getElement().getPositionInFile()),
					subModel);
		}

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

	public static class Interpreted extends ExFlexibleElementModelAddOn.Interpreted<ExElement, ExWithRequiredModels> {
		private ObservableModelElement.ExtModelElement.Interpreted<?> theRequiredModelElement;

		public Interpreted(Def definition, ExElement.Interpreted<? extends ExElement> element) {
			super(definition, element);
		}

		public ObservableModelElement.ExtModelElement.Interpreted<?> getLocalModelElement() {
			return theRequiredModelElement;
		}

		@Override
		public Def getDefinition() {
			return (Def) super.getDefinition();
		}

		@Override
		public void update(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
			super.update(env);
			if (theRequiredModelElement == null || theRequiredModelElement.getDefinition() != getDefinition().getRequiredModelElement()) {
				if (theRequiredModelElement != null)
					theRequiredModelElement.destroy();
				theRequiredModelElement = getDefinition().getRequiredModelElement() == null ? null
					: getDefinition().getRequiredModelElement().interpret(getElement());
			}
			if (theRequiredModelElement != null)
				theRequiredModelElement.update(env);
		}

		@Override
		public Class<ExWithRequiredModels> getInstanceType() {
			return ExWithRequiredModels.class;
		}

		public InterpretedRequiredModelContext getContextConverter(RequiredModelContext context, InterpretedExpressoEnv contextEnv)
			throws ExpressoInterpretationException {
			if (theRequiredModelElement == null || context == null)
				return null;
			Map<ModelComponentId, ContextValueConverter<?>> contextValues = new LinkedHashMap<>();
			populateContextValues(contextValues, theRequiredModelElement, context.getContextValues(), contextEnv);
			return new InterpretedRequiredModelContext(contextValues);
		}

		private void populateContextValues(Map<ModelComponentId, ContextValueConverter<?>> contextValues,
			ObservableModelElement.ExtModelElement.Interpreted<?> required, Map<ModelComponentId, ModelComponentId> compiledContextValues,
			InterpretedExpressoEnv contextEnv) throws ExpressoInterpretationException {
			for (ExtModelValueElement.Interpreted<?, ?> value : required.getValues()) {
				ModelInstanceType<?, ?> type = value.getType();
				ModelComponentId internalValue = getElementValue(value.getDefinition().getModelPath()).getIdentity();
				ModelComponentId contextValue = compiledContextValues.get(internalValue);
				contextValues.put(contextValue, new ContextValueConverter<>(internalValue,
					contextEnv.getModels().getComponent(contextValue).interpret(contextEnv).getType().convert(type, contextEnv)));
			}
			for (ObservableModelElement.ExtModelElement.Interpreted<?> subModel : theRequiredModelElement.getSubModels())
				populateContextValues(contextValues, subModel, compiledContextValues, contextEnv);
		}

		@Override
		public ExWithRequiredModels create(ExElement element) {
			return new ExWithRequiredModels(element);
		}
	}

	private ObservableModelElement.ExtModelElement theLocalModelElement;

	public ExWithRequiredModels(ExElement element) {
		super(element);
	}

	@Override
	public Class<Interpreted> getInterpretationType() {
		return (Class<Interpreted>) (Class<?>) Interpreted.class;
	}

	public ObservableModelElement.ExtModelElement getLocalModelElement() {
		return theLocalModelElement;
	}

	@Override
	public void update(ExAddOn.Interpreted<?, ?> interpreted) {
		super.update(interpreted);

		Interpreted myInterpreted = (Interpreted) interpreted;
		if (myInterpreted.getLocalModelElement() == null)
			theLocalModelElement = null;
		else if (theLocalModelElement == null
			|| theLocalModelElement.getIdentity() != myInterpreted.getLocalModelElement().getDefinition().getIdentity())
			theLocalModelElement = myInterpreted.getLocalModelElement().create(getElement());
		if (theLocalModelElement != null)
			theLocalModelElement.update(myInterpreted.getLocalModelElement(), getElement());
	}

	@Override
	public void instantiated() {
		super.instantiated();
		if (theLocalModelElement != null)
			theLocalModelElement.instantiated();
	}

	@Override
	public void instantiate(ModelSetInstance models) throws ModelInstantiationException {
		super.instantiate(models);
		if (theLocalModelElement != null)
			theLocalModelElement.instantiate(models);
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

	public static class RequiredModelContext {
		private final Map<ModelComponentId, ModelComponentId> theContextValues;

		RequiredModelContext(Map<ModelComponentId, ModelComponentId> contextValues) {
			theContextValues = contextValues;
		}

		public Map<ModelComponentId, ModelComponentId> getContextValues() {
			return Collections.unmodifiableMap(theContextValues);
		}

		public RequiredModelContext and(RequiredModelContext other) {
			if (other == null)
				return this;
			Map<ModelComponentId, ModelComponentId> combo = new LinkedHashMap<>();
			combo.putAll(theContextValues);
			combo.putAll(other.getContextValues());
			return new RequiredModelContext(combo);
		}
	}

	public static class InterpretedRequiredModelContext {
		private final Map<ModelComponentId, ContextValueConverter<?>> theContextValues;

		InterpretedRequiredModelContext(Map<ModelComponentId, ContextValueConverter<?>> contextValues) {
			theContextValues = contextValues;
		}

		public InterpretedRequiredModelContext and(InterpretedRequiredModelContext other) {
			if (other == null)
				return this;
			Map<ModelComponentId, ContextValueConverter<?>> combo = new LinkedHashMap<>();
			combo.putAll(theContextValues);
			combo.putAll(other.theContextValues);
			return new InterpretedRequiredModelContext(combo);
		}

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
			if (!ExFlexibleElementModelAddOn.isElementValueSatisfied(theInternalValue, model)) {
				ExFlexibleElementModelAddOn.satisfyElementValue(theInternalValue, model, //
					theConverter == null ? externalValue : theConverter.convert(externalValue));
			}
		}
	}
}
