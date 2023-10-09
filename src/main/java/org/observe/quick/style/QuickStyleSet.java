package org.observe.quick.style;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.observe.expresso.CompiledExpressoEnv;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.InterpretedExpressoEnv;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExElementTraceable;
import org.observe.expresso.qonfig.ExWithRequiredModels;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.expresso.qonfig.QonfigAttributeGetter;
import org.observe.expresso.qonfig.QonfigChildGetter;
import org.qommons.Named;
import org.qommons.collect.CollectionUtils;
import org.qommons.config.QonfigElement;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;

@ExElementTraceable(toolkit = QuickStyleInterpretation.STYLE, qonfigType = "style-set")
public class QuickStyleSet extends ExElement.Def.Abstract<ExElement.Void> implements Named {
	public static final String STYLE_SET_SESSION_KEY = "Quick.Style.Set";

	private String theName;
	private final List<QuickStyleElement.Def> theStyleElements;

	public QuickStyleSet(QuickStyleSheet styleSheet, QonfigElementOrAddOn styleSetEl) {
		super(styleSheet, styleSetEl);
		theStyleElements = new ArrayList<>();
	}

	@Override
	public QuickStyleSheet getParentElement() {
		return (QuickStyleSheet) super.getParentElement();
	}

	@QonfigAttributeGetter("name")
	@Override
	public String getName() {
		return theName;
	}

	@QonfigChildGetter("style")
	public List<QuickStyleElement.Def> getStyleElements() {
		return Collections.unmodifiableList(theStyleElements);
	}

	public void getStyleValues(Collection<QuickStyleValue> styleValues, StyleApplicationDef application, QonfigElement element,
		CompiledExpressoEnv env, ExWithRequiredModels.RequiredModelContext modelContext) throws QonfigInterpretationException {
		ExWithRequiredModels.RequiredModelContext styleSetModelContext = getAddOn(ExWithRequiredModels.Def.class).getContext(env);
		if (modelContext == null)
			modelContext = styleSetModelContext;
		else
			modelContext = modelContext.and(styleSetModelContext);
		for (QuickStyleElement.Def styleEl : theStyleElements)
			styleEl.getStyleValues(styleValues, application, element, env, modelContext);
	}

	@Override
	protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
		session.put(STYLE_SET_SESSION_KEY, this);
		try {
			super.doUpdate(session);
		} finally {
			session.put(STYLE_SET_SESSION_KEY, null);
		}

		theName = session.getAttributeText("name");
		ExElement.syncDefs(QuickStyleElement.Def.class, theStyleElements, session.forChildren("style"));
	}

	public Interpreted interpret(ExElement.Interpreted<?> parent) {
		return new Interpreted(this, parent);
	}

	public static class Interpreted extends ExElement.Interpreted.Abstract<ExElement.Void> {
		private final List<QuickStyleElement.Interpreted<?>> theStyleElements;

		Interpreted(QuickStyleSet definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
			theStyleElements = new ArrayList<>();
		}

		@Override
		public QuickStyleSet getDefinition() {
			return (QuickStyleSet) super.getDefinition();
		}

		public List<QuickStyleElement.Interpreted<?>> getStyleElements() {
			return Collections.unmodifiableList(theStyleElements);
		}

		public void updateStyleSet(InterpretedExpressoEnv expressoEnv) throws ExpressoInterpretationException {
			update(expressoEnv);
		}

		@Override
		protected void doUpdate(InterpretedExpressoEnv expressoEnv) throws ExpressoInterpretationException {
			super.doUpdate(expressoEnv);

			CollectionUtils
			.synchronize(theStyleElements, getDefinition().getStyleElements(),
				(interp, def) -> interp.getIdentity() == def.getIdentity())//
			.<ExpressoInterpretationException> simpleE(def -> def.interpret(this))//
			.onLeftX(el -> el.getLeftValue().destroy())//
			.onRightX(el -> el.getLeftValue().updateStyle(expressoEnv))//
			.onCommonX(el -> el.getLeftValue().updateStyle(expressoEnv))//
			.rightOrder()//
			.adjust();
		}
	}
}
