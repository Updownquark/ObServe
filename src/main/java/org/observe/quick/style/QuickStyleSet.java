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
import org.qommons.config.QonfigElement;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;

/** A style set containing styles that can be applied to an element by name */
@ExElementTraceable(toolkit = QuickStyleInterpretation.STYLE, qonfigType = "style-set")
public class QuickStyleSet extends ExElement.Def.Abstract<ExElement.Void> implements Named {
	/** The session key in which to install the currently parsing style set */
	public static final String STYLE_SET_SESSION_KEY = "Quick.Style.Set";

	private String theName;
	private final List<QuickStyleElement.Def> theStyleElements;

	/**
	 * @param styleSheet The style sheet that owns this style set
	 * @param styleSetEl The Qonfig type of this style set
	 */
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

	/** @return All style elements in this style set */
	@QonfigChildGetter("style")
	public List<QuickStyleElement.Def> getStyleElements() {
		return Collections.unmodifiableList(theStyleElements);
	}

	/**
	 * Populates all this style set's style values for an element
	 *
	 * @param styleValues The collection to populate
	 * @param application The style application of the parent environment
	 * @param element The environment to get style values for
	 * @param env The expresso environment to evaluate with
	 * @param modelContext The model context for externally-required models
	 * @throws QonfigInterpretationException If the style values could not be compiled
	 */
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

			theName = session.getAttributeText("name");
			syncChildren(QuickStyleElement.Def.class, theStyleElements, session.forChildren("style"));
		} finally {
			session.put(STYLE_SET_SESSION_KEY, null);
		}
	}

	/**
	 * @param parent The parent element for the interpreted style set
	 * @return The interpreted style set
	 */
	public Interpreted interpret(ExElement.Interpreted<?> parent) {
		return new Interpreted(this, parent);
	}

	/** Interpretation of a {@link QuickStyleSet} */
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

		/** @return All style elements in this style set */
		public List<QuickStyleElement.Interpreted<?>> getStyleElements() {
			return Collections.unmodifiableList(theStyleElements);
		}

		/**
		 * Initializes or updates this style set
		 *
		 * @param expressoEnv The expresso environment to interpret expressions with
		 * @throws ExpressoInterpretationException If this style set could not be interpreted
		 */
		public void updateStyleSet(InterpretedExpressoEnv expressoEnv) throws ExpressoInterpretationException {
			update(expressoEnv);
		}

		@Override
		protected void doUpdate(InterpretedExpressoEnv expressoEnv) throws ExpressoInterpretationException {
			super.doUpdate(expressoEnv);

			syncChildren(getDefinition().getStyleElements(), theStyleElements, def -> def.interpret(this),
				(i, sEnv) -> i.updateStyle(sEnv));
		}
	}
}
