package org.observe.expresso.qonfig;

import java.util.LinkedHashMap;
import java.util.Map;

import org.observe.expresso.CompiledExpressoEnv;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.InterpretedExpressoEnv;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelType;
import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.expresso.ObservableModelSet;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.qommons.Identifiable;
import org.qommons.config.QonfigAddOn;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.config.QonfigToolkit;
import org.qommons.ex.ExBiFunction;

public class ExWithElementModel extends ExFlexibleElementModelAddOn<ExElement> {
	public static class Def extends ExFlexibleElementModelAddOn.Def<ExElement, ExWithElementModel> {
		public Def(QonfigAddOn type, ExElement.Def<? extends ExElement> element) {
			super(type, element);
		}

		@Override
		public Map<String, ElementModelValue<?>> getElementValues() {
			return (Map<String, ElementModelValue<?>>) super.getElementValues();
		}

		@Override
		public void update(ExpressoQIS session, ExElement.Def<? extends ExElement> element) throws QonfigInterpretationException {
			super.update(session, element);

			ElementModelValue.Cache dmvCache = session.getElementValueCache();
			Map<String, ElementModelValue.Identity> dynamicValues = new LinkedHashMap<>();
			QonfigToolkit expresso = session.getFocusType().getDeclarer();
			dmvCache.getDynamicValues(expresso, session.getElement().getType(), dynamicValues);
			for (QonfigAddOn inh : session.getElement().getInheritance().values())
				dmvCache.getDynamicValues(expresso, inh, dynamicValues);
			if (!dynamicValues.isEmpty()) {
				// Branch the model lazily. Don't do it if there are no actual element values.
				// This can happen if the values are all attribute-sourced and the attributes are optional
				ObservableModelSet.Builder builder = null;
				for (ElementModelValue.Identity dv : dynamicValues.values()) {
					ExpressoQIS dvSession = session.interpretChild(dv.getDeclaration(), dv.getDeclaration().getType());
					// We need the parent of this element to be the structure that we are currently preparing to create
					dvSession.setElementRepresentation(null);
					ExtModelValueElement.Def<?> spec = dvSession.interpret(ExtModelValueElement.Def.class);
					spec.update(dvSession);
					if (dv.getSourceChild() != null) {
						for (ExpressoQIS childSession : session.asElement(dv.getOwner()).forChildren(dv.getSourceChild()))
							builder = handleDynamicValue(dv, builder, childSession, spec);
					} else
						builder = handleDynamicValue(dv, builder, session.asElement(dv.getOwner()), spec);
				}
			}
		}

		private ObservableModelSet.Builder handleDynamicValue(ElementModelValue.Identity dv, ObservableModelSet.Builder builder,
			ExpressoQIS session, ExtModelValueElement.Def<?> spec) throws QonfigInterpretationException {
			String name;
			if (dv.getNameAttribute() == null) {
				name = dv.getName();
			} else {
				name = session.getElement().getAttributeText(dv.getNameAttribute());
				if (name == null) // If name attribute is not specified, value shall not be declared
					return builder;
				CompiledExpressoEnv env = session.getExpressoEnv();
				env = env.withAttribute(dv.getNameAttribute().getName(), name);
				session.setExpressoEnv(env);
				getElement().setExpressoEnv(env);
			}
			ElementModelValue<?> prev = getElementValues().get(name);
			if (prev != null) {
				throw new QonfigInterpretationException(
					"Multiple conflicting element values named '" + name + "' declared by " + prev.getIdentity() + " and " + dv,
					session.getElement().getPositionInFile(), 0);
			}
			CompiledExpression sourceAttrX;
			try {
				if (dv.isSourceValue())
					sourceAttrX = session.getValueExpression();
				else if (dv.getSourceAttribute() != null)
					sourceAttrX = session.getAttributeExpression(dv.getSourceAttribute());
				else
					sourceAttrX = null;
			} catch (QonfigInterpretationException e) {
				if (dv.isSourceValue())
					throw new QonfigInterpretationException("Could not obtain source value expression for " + dv.getOwner(),
						e.getPosition(), e.getErrorLength(), e);
				else
					throw new QonfigInterpretationException("Could not obtain source-attribute expression for " + dv.getSourceAttribute(),
						e.getPosition(), e.getErrorLength(), e);
			}
			ElementModelValue<?> value;
			if (sourceAttrX != null)
				value = new AttributeBackedElementValue<>(dv, name, spec, sourceAttrX);
			else
				value = new PlaceholderElementValue<>(dv, name, spec);
			if (builder == null)
				builder = createBuilder(session);
			addElementValue(name, value, builder, spec.getElement().getPositionInFile());
			return builder;
		}

		public ElementModelValue.Identity getElementValueId(String elementValueName) throws QonfigInterpretationException {
			return (ElementModelValue.Identity) ((Identifiable) getElementValue(elementValueName)).getIdentity();
		}

		@Override
		public <I extends ExElement.Interpreted<?>, M> void satisfyElementValueType(String elementValueName, ModelType<M> modelType,
			ExBiFunction<I, InterpretedExpressoEnv, ? extends ModelInstanceType<M, ?>, ExpressoInterpretationException> type)
				throws QonfigInterpretationException {
			super.satisfyElementValueType(elementValueName, modelType, type);
		}

		@Override
		public <I extends ExElement.Interpreted<?>, M> void satisfyElementValueType(String elementValueName, ModelInstanceType<M, ?> type)
			throws QonfigInterpretationException {
			super.satisfyElementValueType(elementValueName, type);
		}

		@Override
		public Interpreted interpret(ExElement.Interpreted<? extends ExElement> element) {
			return new Interpreted(this, element);
		}
	}

	public static class Interpreted extends ExFlexibleElementModelAddOn.Interpreted<ExElement, ExWithElementModel> {
		public Interpreted(Def definition, ExElement.Interpreted<? extends ExElement> element) {
			super(definition, element);
		}

		@Override
		public Def getDefinition() {
			return (Def) super.getDefinition();
		}

		@Override
		public <M, MV extends M> void satisfyElementValue(String elementValueName, InterpretedValueSynth<M, MV> satisfier)
			throws ExpressoInterpretationException {
			super.satisfyElementValue(elementValueName, satisfier);
		}

		@Override
		public void satisfyElementValue(String elementValueName, ModelSetInstance models, Object value) throws ModelInstantiationException {
			super.satisfyElementValue(elementValueName, models, value);
		}

		@Override
		public void satisfyElementValue(String elementValueName, ModelSetInstance models, Object value, ActionIfSatisfied ifPreSatisfied)
			throws ModelInstantiationException {
			super.satisfyElementValue(elementValueName, models, value, ifPreSatisfied);
		}

		@Override
		public ExWithElementModel create(ExElement element) {
			return new ExWithElementModel(this, element);
		}
	}

	public ExWithElementModel(Interpreted interpreted, ExElement element) {
		super(interpreted, element);
	}

	@Override
	public void satisfyElementValue(String elementValueName, Object value) throws ModelInstantiationException {
		super.satisfyElementValue(elementValueName, value);
	}

	@Override
	public void satisfyElementValue(String elementValueName, Object value, ActionIfSatisfied ifPreSatisfied)
		throws ModelInstantiationException {
		super.satisfyElementValue(elementValueName, value, ifPreSatisfied);
	}

	@Override
	public void satisfyElementValue(String elementValueName, Object value, ModelSetInstance models) throws ModelInstantiationException {
		super.satisfyElementValue(elementValueName, value, models);
	}

	@Override
	public void satisfyElementValue(String elementValueName, Object value, ModelSetInstance models, ActionIfSatisfied ifPreSatisfied)
		throws ModelInstantiationException {
		super.satisfyElementValue(elementValueName, value, models, ifPreSatisfied);
	}

	static class AttributeBackedElementValue<M> implements ElementModelValue<M> {
		private final ElementModelValue.Identity theId;
		private final String theName;
		private final ExtModelValueElement.Def<M> theSpec;
		private final CompiledExpression theAttributeValue;

		AttributeBackedElementValue(ElementModelValue.Identity id, String name, ExtModelValueElement.Def<M> spec,
			CompiledExpression attributeValue) {
			theId = id;
			theName = name;
			theSpec = spec;
			theAttributeValue = attributeValue;
		}

		@Override
		public ElementModelValue.Identity getDeclaration() {
			return theId;
		}

		@Override
		public ElementModelValue.Identity getIdentity() {
			return theId;
		}

		@Override
		public ModelType<M> getModelType(CompiledExpressoEnv env) {
			return theSpec.getModelType();
		}

		@Override
		public InterpretedValueSynth<M, ?> interpret(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
			ModelInstanceType<M, ?> valueType;
			try {
				valueType = theSpec.getType(env);
			} catch (ExpressoInterpretationException e) {
				throw new ExpressoInterpretationException("Could not interpret type", e.getPosition(), e.getErrorLength(), e);
			}
			try {
				return theAttributeValue.interpret(valueType, env);
			} catch (ExpressoInterpretationException e) {
				String msg = "Could not interpret source"
					+ (theId.isSourceValue() ? " value for " + theId.getOwner() : "-attribute " + theId.getSourceAttribute());
				theSpec.reporting().error(msg, e);
				throw new ExpressoInterpretationException(msg, e.getPosition(), e.getErrorLength(), e);
			}
		}

		@Override
		public String toString() {
			return theName;
		}
	}

	static class PlaceholderElementValue<M> extends ExFlexibleElementModelAddOn.PlaceholderModelValue<M> implements ElementModelValue<M> {
		private final ElementModelValue.Identity theId;
		private final ExtModelValueElement.Def<M> theSpec;

		PlaceholderElementValue(ElementModelValue.Identity id, String name, ExtModelValueElement.Def<M> spec) {
			super(name);
			theId = id;
			theSpec = spec;
		}

		@Override
		public Identity getDeclaration() {
			return theId;
		}

		@Override
		public ElementModelValue.Identity getIdentity() {
			return theId;
		}

		@Override
		public ModelType<M> getModelType() {
			return theSpec.getModelType();
		}

		@Override
		public InterpretedValueSynth<M, ?> interpret(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
			return create(env, theSpec.getType(env));
		}
	}
}
