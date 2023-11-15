package org.observe.quick.style;

import javax.swing.Icon;

import org.observe.SettableValue;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.InterpretedExpressoEnv;
import org.observe.expresso.ModelType.ModelInstanceType.SingleTyped;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableExpression;
import org.observe.expresso.ObservableModelSet;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.qonfig.ExWithRequiredModels;
import org.observe.expresso.qonfig.LocatedExpression;
import org.observe.util.TypeTokens;
import org.qommons.StringUtils;
import org.qommons.config.QonfigElementOrAddOn;

/** The definition of a conditional value for a style attribute in quick */
public class QuickStyleValue implements Comparable<QuickStyleValue> {
	private final QuickStyleSheet theStyleSheet;
	private final QuickStyleSet theStyleSet;
	private final StyleApplicationDef theApplication;
	private final QuickStyleAttributeDef theAttribute;
	private final LocatedExpression theValueExpression;
	private final boolean isTrickleDown;
	private final ExWithRequiredModels.RequiredModelContext theModelContext;

	/**
	 * @param styleSheet The style sheet that defined this value
	 * @param styleSet The style set that defined this value
	 * @param application The application of this value
	 * @param attribute THe attribute this value is for
	 * @param value The expression defining this style value's value
	 */
	public QuickStyleValue(QuickStyleSheet styleSheet, QuickStyleSet styleSet, StyleApplicationDef application,
		QuickStyleAttributeDef attribute, LocatedExpression value) {
		this(styleSheet, styleSet, application, attribute, value, null);
	}

	private QuickStyleValue(QuickStyleSheet styleSheet, QuickStyleSet styleSet, StyleApplicationDef application,
		QuickStyleAttributeDef attribute, LocatedExpression value, ExWithRequiredModels.RequiredModelContext modelContext) {
		if (attribute == null)
			throw new NullPointerException("Attribute is null");
		else if (value == null)
			throw new NullPointerException("Value is null");
		theStyleSheet = styleSheet;
		theStyleSet = styleSet;
		theApplication = application;
		theAttribute = attribute;
		theValueExpression = value;
		boolean styleAppliesToApp = false;
		if (application.getRole() != null && application.getRole().getType() != null
			&& attribute.getDeclarer().getElement().isAssignableFrom(application.getRole().getType()))
			styleAppliesToApp = true;
		else {
			for (QonfigElementOrAddOn type : application.getTypes().values()) {
				styleAppliesToApp = attribute.getDeclarer().getElement().isAssignableFrom(type);
				if (styleAppliesToApp)
					break;
			}
		}
		isTrickleDown = !styleAppliesToApp;
		theModelContext = modelContext;
	}

	public QuickStyleValue withModelContext(ExWithRequiredModels.RequiredModelContext modelContext) {
		if (modelContext == null)
			return this;
		if (theModelContext != null)
			modelContext = theModelContext.and(modelContext);
		return new QuickStyleValue(theStyleSheet, theStyleSet, theApplication, theAttribute, theValueExpression, modelContext);
	}

	public QuickStyleValue forInherited(ObservableModelSet inherited) {
		if (theModelContext == null)
			return this;
		ExWithRequiredModels.RequiredModelContext inhCtx = theModelContext.forInherited(inherited);
		if (inhCtx == theModelContext)
			return this;
		return new QuickStyleValue(theStyleSheet, theStyleSet, theApplication, theAttribute, theValueExpression, inhCtx);
	}

	/** @return The style sheet that defined this value */
	public QuickStyleSheet getStyleSheet() {
		return theStyleSheet;
	}

	/** @return The style set that defined this value */
	public QuickStyleSet getStyleSet() {
		return theStyleSet;
	}

	/**
	 * @return The application of this value, determining which elements it
	 *         {@link StyleApplicationDef#applies(org.qommons.config.QonfigElement) applies} to and
	 *         {@link StyleApplicationDef#getCondition() when}
	 */
	public StyleApplicationDef getApplication() {
		return theApplication;
	}

	/** @return The style attribute this value is for */
	public QuickStyleAttributeDef getAttribute() {
		return theAttribute;
	}

	/** @return The expression defining this style value's value */
	public LocatedExpression getValueExpression() {
		return theValueExpression;
	}

	/**
	 * <p>
	 * It is possible to specify style attributes from style sheets against types that the attribute is not declared by. In this case, the
	 * style should apply to the highest-level descendants that the attribute applies to, under those elements which match the style.
	 * </p>
	 * <p>
	 * E.g. one could specify a style for widget color against a table column. A table column is not a widget. Therefore, clearly the intent
	 * is to apply the color style against all the widgets belonging to the column, i.e. renderers and editors.
	 * </p>
	 *
	 * @return Whether this style value should apply to the highest-level descendants that the attribute applies to of elements which match
	 *         the application
	 */
	public boolean isTrickleDown() {
		return isTrickleDown;
	}

	/**
	 * @param appCache The interpreted style cache to avoid duplicating applications
	 * @param env The expresso environment with which to
	 *        {@link ObservableExpression#evaluate(org.observe.expresso.ModelType.ModelInstanceType, InterpretedExpressoEnv, int, org.qommons.ex.ExceptionHandler.Double)
	 *        evaluate} this value's expressions
	 * @return The compiled style value
	 * @throws ExpressoInterpretationException If the expressions could not be compiled
	 */
	public InterpretedStyleValue<?> interpret(InterpretedExpressoEnv env, QuickStyleSheet.Interpreted styleSheet,
		QuickInterpretedStyleCache.Applications appCache) throws ExpressoInterpretationException {
		if (theStyleSheet == null)
			styleSheet = null;
		else
			styleSheet = styleSheet.findInterpretation(theStyleSheet);
		QuickStyleSet.Interpreted styleSet = theStyleSet == null ? null : styleSheet.getStyleSets().get(theStyleSet.getName());
		InterpretedExpressoEnv styleEnv;
		if (styleSet != null)
			styleEnv = styleSet.getExpressoEnv();
		else if (styleSheet != null)
			styleEnv = styleSheet.getExpressoEnv();
		else
			styleEnv = env;
		InterpretedStyleApplication application = appCache.getApplication(theApplication, styleEnv);
		QuickInterpretedStyleCache cache = QuickInterpretedStyleCache.get(styleEnv);
		QuickStyleAttribute<?> attribute = cache.getAttribute(theAttribute, styleEnv);
		return _interpret(application, attribute, styleSheet, styleEnv, env);
	}

	private <T> InterpretedStyleValue<T> _interpret(InterpretedStyleApplication application, QuickStyleAttribute<T> attribute,
		QuickStyleSheet.Interpreted styleSheet, InterpretedExpressoEnv styleEnv, InterpretedExpressoEnv contextEnv)
			throws ExpressoInterpretationException {
		InterpretedValueSynth<SettableValue<?>, SettableValue<T>> value = interpretStyleValue(theValueExpression,
			ModelTypes.Value.forType(attribute.getType()), styleEnv);
		ExWithRequiredModels.InterpretedRequiredModelContext modelContext = null;
		if (theModelContext == null)
			modelContext = null;
		else {
			if (theStyleSheet != null) {
				styleSheet = styleSheet.findInterpretation(theStyleSheet);
				ExWithRequiredModels.Interpreted reqModels = styleSheet.getAddOn(ExWithRequiredModels.Interpreted.class);
				modelContext = reqModels.getContextConverter(theModelContext, contextEnv);
			}
			if (theStyleSet != null) {
				QuickStyleSet.Interpreted styleSet = styleSheet.getStyleSets().get(theStyleSet.getName());
				ExWithRequiredModels.Interpreted reqModels = styleSet.getAddOn(ExWithRequiredModels.Interpreted.class);
				if (modelContext == null)
					modelContext = reqModels.getContextConverter(theModelContext, contextEnv);
				else
					modelContext = modelContext.and(reqModels.getContextConverter(theModelContext, contextEnv));
			}
		}
		return new InterpretedStyleValue<>(this, application, attribute, value, modelContext);
	}

	private <T> InterpretedValueSynth<SettableValue<?>, SettableValue<T>> interpretStyleValue(LocatedExpression valueExpression,
		SingleTyped<SettableValue<?>, T, SettableValue<T>> type, InterpretedExpressoEnv env) throws ExpressoInterpretationException {
		if (TypeTokens.getRawType(type.getType(0)) == Icon.class)
			return (InterpretedValueSynth<SettableValue<?>, SettableValue<T>>) (InterpretedValueSynth<?, ?>) QuickStyleUtils
				.evaluateIcon(valueExpression, env);
		else
			return valueExpression.interpret(type, env);
	}

	/**
	 * @param application The application to apply
	 * @return A new style value identical to this one, except whose {@link #getApplication() application} is the
	 *         {@link StyleApplicationDef#and(StyleApplicationDef) AND} operation of this value's application and the one given
	 */
	public QuickStyleValue when(StyleApplicationDef application) {
		return new QuickStyleValue(theStyleSheet, theStyleSet, theApplication.and(application), theAttribute, theValueExpression,
			theModelContext);
	}

	@Override
	public int compareTo(QuickStyleValue o) {
		int comp = theApplication.compareTo(o.theApplication);
		// Compare the source style sheets
		if (comp == 0) {
			if (theStyleSheet == null) {
				if (o.theStyleSheet != null)
					comp = -1; // Style values specified on the element have highest priority
			} else if (o.theStyleSheet == null)
				comp = 1; // Style values specified on the element have highest priority
		}
		// Last, compare the attributes, just multiple attribute values with identical priority
		// can live in the same style sheet together
		if (comp == 0)
			comp = StringUtils.compareNumberTolerant(//
				theAttribute.getDeclarer().getElement().getDeclarer().getLocation().toString(), //
				o.theAttribute.getDeclarer().getElement().getDeclarer().getLocation().toString(), true, true);
		if (comp == 0)
			comp = StringUtils.compareNumberTolerant(//
				theAttribute.getDeclarer().getElement().getName(), //
				o.theAttribute.getDeclarer().getElement().getName(), true, true);
		if (comp == 0)
			comp = StringUtils.compareNumberTolerant(theAttribute.getName(), o.theAttribute.getName(), true, true);
		return comp;
	}

	@Override
	public String toString() {
		StringBuilder str = new StringBuilder(theApplication.toString());
		if (str.length() > 0)
			str.append(':');
		str.append(theAttribute.getName()).append('=').append(theValueExpression).toString();
		return str.toString();
	}
}
