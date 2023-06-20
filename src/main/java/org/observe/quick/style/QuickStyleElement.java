package org.observe.quick.style;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.observe.SettableValue;
import org.observe.expresso.CompiledExpression;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ExpressoQIS;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableExpression;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.qonfig.ExElement;
import org.observe.util.TypeTokens;
import org.qommons.collect.CollectionUtils;
import org.qommons.config.AbstractQIS;
import org.qommons.config.QonfigChildDef;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;

public class QuickStyleElement extends ExElement.Abstract {
	private static final ExElement.AttributeValueGetter<QuickStyleElement, Interpreted, Def> ELEMENT//
	= ExElement.AttributeValueGetter.of(Def::getStyleElement, i -> null, e -> null,
		"The element or add-on type that the style applies to");
	private static final ExElement.AttributeValueGetter<QuickStyleElement, Interpreted, Def> ROLE//
	= ExElement.AttributeValueGetter.of(Def::getChild, i -> null, e -> null, "The child role that the style applies to");
	private static final ExElement.AttributeValueGetter.Expression<QuickStyleElement, Interpreted, Def, SettableValue<?>, SettableValue<Boolean>> CONDITION//
	= ExElement.AttributeValueGetter.ofX(Def::getCondition, Interpreted::getCondition, QuickStyleElement::getCondition,
		"The runtime condition for the style.  A style will only be applied when its condition, if present, is true.");
	private static final ExElement.AttributeValueGetter<QuickStyleElement, Interpreted, Def> STYLE_SET//
	= ExElement.AttributeValueGetter.of(Def::getStyleSet, i -> null, e -> null, "The style set to apply when this style applies");
	private static final ExElement.AttributeValueGetter<QuickStyleElement, Interpreted, Def> ATTRIBUTE//
	= ExElement.AttributeValueGetter.of(Def::getDeclaredAttribute, i -> null, e -> null,
		"The attribute whose value to change when this style applies");
	private static final ExElement.AttributeValueGetter.Expression<QuickStyleElement, Interpreted, Def, SettableValue<?>, SettableValue<?>> VALUE//
	= ExElement.AttributeValueGetter.ofX(Def::getValue, Interpreted::getValue, QuickStyleElement::getValue,
		"The value for the style's attribute, when it applies");
	private static final ExElement.ChildElementGetter<QuickStyleElement, Interpreted, Def> STYLE_ELEMENTS = ExElement.ChildElementGetter
		.<QuickStyleElement, Interpreted, Def> of(Def::getChildren, Interpreted::getChildren, null,
			"Styles declared on the element itself");

	public static class Def extends ExElement.Def.Abstract<QuickStyleElement> {
		private final QonfigElementOrAddOn theStyleElement;
		private final QonfigChildDef theChild;
		private final CompiledExpression theCondition;
		private final QuickStyleSet theStyleSet;
		private final QuickStyleAttribute<?> theDeclaredAttribute;
		private final QuickStyleAttribute<?> theEffectiveAttribute;
		private final CompiledExpression theValue;
		private final List<Def> theChildren;

		public Def(ExElement.Def<?> parent, AbstractQIS<?> session, QonfigElementOrAddOn styleElement, QonfigChildDef child,
			CompiledExpression condition, QuickStyleSet styleSet, QuickStyleAttribute<?> declaredAttribute,
			QuickStyleAttribute<?> effectiveAttribute, CompiledExpression value, List<Def> children) {
			super(parent, session.getElement());
			forAttribute(session.getAttributeDef(null, null, "element"), ELEMENT);
			forAttribute(session.getAttributeDef(null, null, "child"), ROLE);
			forAttribute(session.getAttributeDef(null, null, "condition"), CONDITION);
			forAttribute(session.getAttributeDef(null, null, "style-set"), STYLE_SET);
			forAttribute(session.getAttributeDef(null, null, "attr"), ATTRIBUTE);
			forAttribute(session.getAttributeDef(null, null, "element"), ELEMENT);
			forValue(VALUE);
			forChild(session.getRole("sub-style"), STYLE_ELEMENTS);
			theStyleElement = styleElement;
			theChild = child;
			theCondition = condition;
			theStyleSet = styleSet;
			theDeclaredAttribute = declaredAttribute;
			theEffectiveAttribute = effectiveAttribute;
			theValue = value;
			theChildren = children;
		}

		public QonfigElementOrAddOn getStyleElement() {
			return theStyleElement;
		}

		public QonfigChildDef getChild() {
			return theChild;
		}

		public CompiledExpression getCondition() {
			return theCondition;
		}

		public QuickStyleSet getStyleSet() {
			return theStyleSet;
		}

		public QuickStyleAttribute<?> getDeclaredAttribute() {
			return theDeclaredAttribute;
		}

		public QuickStyleAttribute<?> getEffectiveAttribute() {
			return theEffectiveAttribute;
		}

		public CompiledExpression getValue() {
			return theValue;
		}

		public List<Def> getChildren() {
			return theChildren;
		}

		@Override
		public void update(ExpressoQIS session) throws QonfigInterpretationException {
			super.update(session);
			int i = 0;
			for (ExpressoQIS subStyleSession : session.forChildren("sub-style"))
				theChildren.get(i++).update(subStyleSession);
		}

		public Interpreted interpret(ExElement.Interpreted<?> parent) {
			return new Interpreted(this, parent);
		}
	}

	public static class Interpreted extends ExElement.Interpreted.Abstract<QuickStyleElement> {
		private InterpretedValueSynth<SettableValue<?>, SettableValue<Boolean>> theCondition;
		private InterpretedValueSynth<SettableValue<?>, ? extends SettableValue<?>> theValue;
		private final List<Interpreted> theChildren;

		public Interpreted(Def definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
			theChildren = new ArrayList<>();
		}

		@Override
		public Def getDefinition() {
			return (Def) super.getDefinition();
		}

		public InterpretedValueSynth<SettableValue<?>, SettableValue<Boolean>> getCondition() {
			return theCondition;
		}

		public void setCondition(InterpretedValueSynth<SettableValue<?>, SettableValue<Boolean>> condition) {
			theCondition = condition;
		}

		public InterpretedValueSynth<SettableValue<?>, ? extends SettableValue<?>> getValue() {
			return theValue;
		}

		public void setValue(InterpretedValueSynth<SettableValue<?>, SettableValue<?>> value) {
			theValue = value;
		}

		public List<Interpreted> getChildren() {
			return Collections.unmodifiableList(theChildren);
		}

		@Override
		public void update() throws ExpressoInterpretationException {
			super.update();
			theCondition = getDefinition().getCondition() == null ? null
				: getDefinition().getCondition().evaluate(ModelTypes.Value.BOOLEAN).interpret();
			if (getDefinition().getValue() != null && getDefinition().getValue().getExpression() != ObservableExpression.EMPTY)
				theValue = getDefinition().getValue().evaluate(ModelTypes.Value.forType(getDefinition().getEffectiveAttribute().getType()))
					.interpret();
			else
				theValue = null;
			CollectionUtils.synchronize(theChildren, getDefinition().getChildren(), (interp, def) -> interp.getDefinition() == def)//
			.<ExpressoInterpretationException> simpleE(def -> def.interpret(this))//
			.commonUses(true, false)//
			.rightOrder()//
			.onLeftX(el -> el.getLeftValue().destroy())//
			.onRightX(el -> el.getLeftValue().update())//
			.onCommonX(el -> el.getLeftValue().update())//
			.adjust();
		}
	}

	private final SettableValue<SettableValue<Boolean>> theCondition;
	private final SettableValue<? extends SettableValue<?>> theValue;

	public QuickStyleElement(Interpreted interpreted, ExElement parent) {
		super(interpreted, parent);
		theCondition = SettableValue
			.build(TypeTokens.get().keyFor(SettableValue.class).<SettableValue<Boolean>> parameterized(boolean.class)).build();
		theValue = SettableValue.build(TypeTokens.get().keyFor(SettableValue.class)
			.<SettableValue<?>> parameterized(interpreted.getDefinition().getEffectiveAttribute().getType())).build();
	}

	public SettableValue<Boolean> getCondition() {
		return SettableValue.flatten(theCondition, () -> true);
	}

	public SettableValue<?> getValue() {
		return SettableValue.flatten((SettableValue<SettableValue<Object>>) theValue);
	}
}
