package org.observe.expresso.qonfig;

import org.qommons.config.PatternMatch;
import org.qommons.config.QonfigAddOn;
import org.qommons.config.QonfigAttributeDef;
import org.qommons.config.QonfigElementDef;
import org.qommons.config.QonfigInterpretationException;

/** A external model value backed by an attribute specified on the promise that resulted in this element being loaded */
public class AttributeBackedModelValue extends ExAddOn.Abstract<ModelValueElement<?>> {
	/** The XML name of this element */
	public static final String ATTR_BACKED_MODEL_VALUE = "attr-backed-model-value";

	/** {@link AttributeBackedModelValue} definition */
	@ExElementTraceable(toolkit = ExpressoSessionImplV0_1.CORE,
		qonfigType = ATTR_BACKED_MODEL_VALUE,
		interpretation = Interpreted.class,
		instance = AttributeBackedModelValue.class)
	public static class Def extends ExAddOn.Def.Abstract<ModelValueElement<?>, AttributeBackedModelValue> {
		private QonfigAttributeDef theSourceAttribute;

		/**
		 * @param type The Qonfig type of this add-on
		 * @param element The model value element
		 */
		public Def(QonfigAddOn type, ExElement.Def<?> element) {
			super(type, element);
		}

		/** @return The source attribute to use to provide the value for this model value */
		@QonfigAttributeGetter("source-attr")
		public QonfigAttributeDef getSourceAttribute() {
			return theSourceAttribute;
		}

		@Override
		public void update(ExpressoQIS session, ExElement.Def<? extends ModelValueElement<?>> element)
			throws QonfigInterpretationException {
			super.update(session, element);

			ExElement.Def<?> parent = element.getParentElement();
			while (parent != null && !(parent instanceof QonfigExternalDocument.Def))
				parent = parent.getParentElement();
			if (parent == null) {
				element.reporting().warn(ATTR_BACKED_MODEL_VALUE + " applied to non-external element");
				return;
			}
			QonfigElementDef fulfills = ((QonfigExternalDocument.Def<?>) parent).getFulfills();
			PatternMatch sourceAttr = session.getAttribute("source-attr", PatternMatch.class);
			String elementName = sourceAttr.getGroup("name");
			if (elementName != null) {
				String ns = sourceAttr.getGroup("ns");
				if (ns != null)
					elementName = ns + ":" + elementName;
				theSourceAttribute = element.getElement().getDocument().getDocToolkit().getAttribute(elementName,
					sourceAttr.getGroup("member"));
				if (theSourceAttribute != null && !theSourceAttribute.getOwner().isAssignableFrom(fulfills))
					session.reporting().at(session.attributes().get("source-attr").getContent())
					.error("Attribute '" + sourceAttr.getWholeText() + "' does not apply to fulfillment " + fulfills);
				if (theSourceAttribute == null)
					session.reporting().at(session.attributes().get("source-attr").getContent())
					.error("No such attribute found: " + sourceAttr.getWholeText());
			} else {
				theSourceAttribute = fulfills.getAttribute(sourceAttr.getWholeText());
				if (theSourceAttribute == null)
					session.reporting().at(session.attributes().get("source-attr").getContent())
					.error("No such attribute found: " + fulfills + "." + sourceAttr.getWholeText());
			}
		}

		@Override
		public Interpreted interpret(ExElement.Interpreted<?> element) {
			return new Interpreted(this, element);
		}
	}

	/** {@link AttributeBackedModelValue} interpretation */
	public static class Interpreted extends ExAddOn.Interpreted.Abstract<ModelValueElement<?>, AttributeBackedModelValue> {
		Interpreted(Def definition, ExElement.Interpreted<?> element) {
			super(definition, element);
		}

		@Override
		public Def getDefinition() {
			return (Def) super.getDefinition();
		}

		@Override
		public Class<AttributeBackedModelValue> getInstanceType() {
			return AttributeBackedModelValue.class;
		}

		@Override
		public AttributeBackedModelValue create(ModelValueElement<?> element) {
			return null;
		}
	}

	AttributeBackedModelValue(ModelValueElement<?> element) {
		super(element);
	}

	@Override
	public Class<Interpreted> getInterpretationType() {
		return Interpreted.class;
	}
}
