package org.observe.quick.style;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.observe.SettableValue;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableExpression;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.qonfig.CompiledExpression;
import org.observe.expresso.qonfig.ElementTypeTraceability;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.expresso.qonfig.QonfigAttributeGetter;
import org.observe.expresso.qonfig.QonfigChildGetter;
import org.observe.util.TypeTokens;
import org.qommons.collect.CollectionUtils;
import org.qommons.config.AbstractQIS;
import org.qommons.config.QonfigChildDef;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;

public class QuickStyleElement extends ExElement.Abstract {
	private static final ElementTypeTraceability<QuickStyleElement, Interpreted, Def> TRACEABILITY = ElementTypeTraceability
		.<QuickStyleElement, Interpreted, Def> build(StyleSessionImplV0_1.NAME, StyleSessionImplV0_1.VERSION, "style")//
		.reflectMethods(Def.class, Interpreted.class, QuickStyleElement.class)//
		.build();

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
			theStyleElement = styleElement;
			theChild = child;
			theCondition = condition;
			theStyleSet = styleSet;
			theDeclaredAttribute = declaredAttribute;
			theEffectiveAttribute = effectiveAttribute;
			theValue = value;
			theChildren = children;
		}

		@QonfigAttributeGetter("element")
		public QonfigElementOrAddOn getStyleElement() {
			return theStyleElement;
		}

		@QonfigAttributeGetter("child")
		public QonfigChildDef getChild() {
			return theChild;
		}

		@QonfigAttributeGetter("condition")
		public CompiledExpression getCondition() {
			return theCondition;
		}

		@QonfigAttributeGetter("style-set")
		public QuickStyleSet getStyleSet() {
			return theStyleSet;
		}

		@QonfigAttributeGetter("attr")
		public QuickStyleAttribute<?> getDeclaredAttribute() {
			return theDeclaredAttribute;
		}

		public QuickStyleAttribute<?> getEffectiveAttribute() {
			return theEffectiveAttribute;
		}

		@QonfigAttributeGetter
		public CompiledExpression getValue() {
			return theValue;
		}

		@QonfigChildGetter("sub-style")
		public List<Def> getChildren() {
			return theChildren;
		}

		@Override
		public void update(ExpressoQIS session) throws QonfigInterpretationException {
			withTraceability(TRACEABILITY.validate(session.getFocusType(), session.reporting()));
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

		public InterpretedValueSynth<SettableValue<?>, ? extends SettableValue<?>> getValue() {
			return theValue;
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

		public QuickStyleElement create(ExElement parent) {
			return new QuickStyleElement(this, parent);
		}
	}

	private final SettableValue<SettableValue<Boolean>> theCondition;
	private final SettableValue<? extends SettableValue<?>> theValue;
	private final List<QuickStyleElement> theChildren;

	public QuickStyleElement(Interpreted interpreted, ExElement parent) {
		super(interpreted, parent);
		theCondition = SettableValue
			.build(TypeTokens.get().keyFor(SettableValue.class).<SettableValue<Boolean>> parameterized(boolean.class)).build();
		QuickStyleAttribute<?> attr = interpreted.getDefinition().getEffectiveAttribute();
		if (attr == null)
			theValue = null;
		else
			theValue = SettableValue.build(TypeTokens.get().keyFor(SettableValue.class).<SettableValue<?>> parameterized(attr.getType()))
			.build();
		theChildren = new ArrayList<>();
	}

	public SettableValue<Boolean> getCondition() {
		return SettableValue.flatten(theCondition, () -> true);
	}

	public SettableValue<?> getValue() {
		if (theValue == null)
			return SettableValue.of(Object.class, null, "Unsettable");
		else
			return SettableValue.flatten((SettableValue<SettableValue<Object>>) theValue);
	}

	public List<QuickStyleElement> getChildren() {
		return Collections.unmodifiableList(theChildren);
	}

	@Override
	protected void updateModel(ExElement.Interpreted<?> interpreted, ModelSetInstance myModels) throws ModelInstantiationException {
		super.updateModel(interpreted, myModels);
		Interpreted myInterpreted = (Interpreted) interpreted;
		theCondition.set(myInterpreted.getCondition() == null ? null : myInterpreted.getCondition().get(myModels), null);
		if (theValue != null)
			((SettableValue<SettableValue<?>>) theValue)
			.set(myInterpreted.getValue() == null ? null : myInterpreted.getValue().get(myModels), null);
		CollectionUtils
		.synchronize(theChildren, myInterpreted.getChildren(),
			(inst, interp) -> inst.getIdentity() == interp.getDefinition().getIdentity())//
		.<ModelInstantiationException> simpleE(interp -> interp.create(this))//
		.onRightX(el -> el.getLeftValue().update(el.getRightValue(), myModels))
		.onCommonX(el -> el.getLeftValue().update(el.getRightValue(), myModels)).adjust();
	}
}
