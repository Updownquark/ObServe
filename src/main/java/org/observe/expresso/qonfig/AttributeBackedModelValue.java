package org.observe.expresso.qonfig;

import org.qommons.config.PatternMatch;
import org.qommons.config.QonfigAddOn;
import org.qommons.config.QonfigAttributeDef;
import org.qommons.config.QonfigElementDef;
import org.qommons.config.QonfigInterpretationException;

public class AttributeBackedModelValue extends ExAddOn.Abstract<ModelValueElement<?, ?>> {
	public static final String ATTR_BACKED_MODEL_VALUE = "attr-backed-model-value";

	@ExElementTraceable(toolkit = ExpressoSessionImplV0_1.CORE,
		qonfigType = ATTR_BACKED_MODEL_VALUE,
		interpretation = Interpreted.class,
		instance = AttributeBackedModelValue.class)
	public static class Def extends ExAddOn.Def.Abstract<ModelValueElement<?, ?>, AttributeBackedModelValue> {
		private QonfigAttributeDef theSourceAttribute;

		public Def(QonfigAddOn type, ExElement.Def<? extends ModelValueElement<?, ?>> element) {
			super(type, element);
		}

		@QonfigAttributeGetter("source-attr")
		public QonfigAttributeDef getSourceAttribute() {
			return theSourceAttribute;
		}

		@Override
		public void update(ExpressoQIS session, ExElement.Def<? extends ModelValueElement<?, ?>> element)
			throws QonfigInterpretationException {
			super.update(session, element);

			ExElement.Def<?> parent = element.getParentElement();
			while (parent != null && !(parent instanceof QonfigExternalContent.Def))
				parent = parent.getParentElement();
			if (parent == null) {
				element.reporting().warn(ATTR_BACKED_MODEL_VALUE + " applied to non-external element");
				return;
			}
			QonfigElementDef fulfills = ((QonfigExternalContent.Def<?>) parent).getFulfills();
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
		public Interpreted interpret(ExElement.Interpreted<? extends ModelValueElement<?, ?>> element) {
			return new Interpreted(this, element);
		}
	}

	public static class Interpreted extends ExAddOn.Interpreted.Abstract<ModelValueElement<?, ?>, AttributeBackedModelValue> {
		Interpreted(Def definition, ExElement.Interpreted<? extends ModelValueElement<?, ?>> element) {
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
		public AttributeBackedModelValue create(ModelValueElement<?, ?> element) {
			return null;
		}
	}

	AttributeBackedModelValue(ModelValueElement<?, ?> element) {
		super(element);
	}

	@Override
	public Class<Interpreted> getInterpretationType() {
		return Interpreted.class;
	}
}
