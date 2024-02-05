package org.observe.quick.style;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.observe.SettableValue;
import org.observe.expresso.CompiledExpressoEnv;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.InterpretedExpressoEnv;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableExpression;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ModelValueInstantiator;
import org.observe.expresso.qonfig.ElementModelValue;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExElementTraceable;
import org.observe.expresso.qonfig.ExWithRequiredModels;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.expresso.qonfig.LocatedExpression;
import org.observe.expresso.qonfig.QonfigAttributeGetter;
import org.observe.expresso.qonfig.QonfigChildGetter;
import org.observe.quick.style.QuickTypeStyle.TypeStyleSet;
import org.observe.util.TypeTokens;
import org.qommons.MultiInheritanceSet;
import org.qommons.collect.CollectionUtils;
import org.qommons.config.QonfigAddOn;
import org.qommons.config.QonfigAttributeDef;
import org.qommons.config.QonfigChildDef;
import org.qommons.config.QonfigElement;
import org.qommons.config.QonfigElement.QonfigValue;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.config.QonfigToolkit;
import org.qommons.io.ErrorReporting;
import org.qommons.io.LocatedFilePosition;

/**
 * A &lt;style> element affecting one or more elements' style
 *
 * @param <T> The type of the style's attribute (if any)
 */
public class QuickStyleElement<T> extends ExElement.Abstract {
	/** The XML name of this element */
	public static final String STYLE = "style";
	/** Session property in which to cache the {@link QuickTypeStyle.TypeStyleSet} */
	public static final String STYLE_TYPE_SET = "Quick.Style.Type.Set";

	/** Definition for a {@link QuickStyledElement} */
	@ExElementTraceable(toolkit = QuickStyleInterpretation.STYLE,
		qonfigType = STYLE,
		interpretation = Interpreted.class,
		instance = QuickStyleElement.class)
	public static class Def extends ExElement.Def.Abstract<QuickStyleElement<?>> {
		private QonfigElementOrAddOn theStyleElement;
		private List<QonfigChildDef> theRoles;
		private LocatedExpression theCondition;
		private QuickStyleSet theStyleSet;
		private QuickStyleAttributeDef theDeclaredAttribute;
		private QuickStyleAttributeDef theEffectiveAttribute;
		private LocatedExpression theValue;
		private StyleApplicationDef theApplication;
		private final List<Def> theChildren;
		private final List<QuickStyleValue> theStyleValues;

		/**
		 * @param parent The parent of this style element
		 * @param qonfigType The Qonfig type of this style element
		 */
		public Def(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType);
			theRoles = new ArrayList<>();
			theChildren = new ArrayList<>();
			theStyleValues = new ArrayList<>();
		}

		/** @return The type of element that this style element will affect style for */
		@QonfigAttributeGetter("element")
		public QonfigElementOrAddOn getStyleElement() {
			return theStyleElement;
		}

		/** @return Child roles that an element must fulfill to be affected by this style */
		@QonfigAttributeGetter("child")
		public List<QonfigChildDef> getRoles() {
			return Collections.unmodifiableList(theRoles);
		}

		/** @return A condition that must be true for this style to be effective */
		@QonfigAttributeGetter("if")
		public LocatedExpression getCondition() {
			return theCondition;
		}

		/** @return The style set that this style belongs to */
		@QonfigAttributeGetter("style-set")
		public QuickStyleSet getStyleSet() {
			return theStyleSet;
		}

		/** @return The style attribute declared by this element */
		@QonfigAttributeGetter("attr")
		public QuickStyleAttributeDef getDeclaredAttribute() {
			return theDeclaredAttribute;
		}

		/** @return The style attribute that this style is for, if any */
		public QuickStyleAttributeDef getEffectiveAttribute() {
			return theEffectiveAttribute;
		}

		/** @return The expression containing the value for the style */
		@QonfigAttributeGetter
		public LocatedExpression getValue() {
			return theValue;
		}

		/** @return The style application of this element */
		public StyleApplicationDef getApplication() {
			return theApplication;
		}

		/** @return All sub-styles on this element */
		@QonfigChildGetter("sub-style")
		public List<Def> getChildren() {
			return Collections.unmodifiableList(theChildren);
		}

		/**
		 * Populates conditional values for this style element (and its children)
		 *
		 * @param values The values collection to populate
		 * @param application The style application of the parent environment
		 * @param element The element to get style values for
		 * @param env The expresso environment of the evaluation
		 * @param modelContext Model context for expected model values
		 * @throws QonfigInterpretationException If the style values could not be compiled
		 */
		public void getStyleValues(Collection<QuickStyleValue> values, StyleApplicationDef application, QonfigElement element,
			CompiledExpressoEnv env, ExWithRequiredModels.RequiredModelContext modelContext) throws QonfigInterpretationException {
			if (theApplication.applies(element)) {
				for (QuickStyleValue value : theStyleValues) {
					if (value.getApplication().applies(element))
						values.add(value.when(application).withModelContext(modelContext));
				}
				if (theStyleSet != null)
					theStyleSet.getStyleValues(values, theApplication.and(application), element, env.at(reporting().getFileLocation()),
						modelContext);
			} else if (application.equals(StyleApplicationDef.ALL)) {
				for (QuickStyleValue value : theStyleValues)
					if (value.getApplication().applies(element))
						values.add(value.withModelContext(modelContext));
			}
			for (QuickStyleElement.Def child : theChildren)
				child.getStyleValues(values, application, element, env, modelContext);
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			super.doUpdate(session);

			QuickStyleElement.Def parent;
			QonfigElement targetElement;
			{
				ExElement.Def<?> parentEl = getParentElement();
				if (parentEl instanceof QuickStyleElement.Def)
					parent = (QuickStyleElement.Def) parentEl;
				else
					parent = null;
				while (parentEl instanceof QuickStyleElement.Def)
					parentEl = parentEl.getParentElement();
				if (parentEl instanceof QuickStyleSet || parentEl instanceof QuickStyleSheet)
					targetElement = null;
				else
					targetElement = parentEl.getElement();
			}
			QuickStyleSheet styleSheet = session.get(ExWithStyleSheet.QUICK_STYLE_SHEET, QuickStyleSheet.class);
			QuickStyleSheet ancestorSS = null;
			for (ExElement.Def<?> p = getParentElement(); p != null; p = p.getParentElement()) {
				if (p instanceof QuickStyleSheet) {
					ancestorSS = (QuickStyleSheet) p;
					break;
				}
			}

			StyleApplicationDef application = parent == null ? StyleApplicationDef.ALL : parent.getApplication();
			theRoles.clear();
			QonfigValue rolePath = session.attributes().get("child").get();
			if (rolePath != null && rolePath.value != null) { // Role path may be defaulted
				if (application == null)
					throw new QonfigInterpretationException("Cannot specify a style role without a type above it", //
						rolePath.position == null ? null : new LocatedFilePosition(rolePath.fileLocation, rolePath.position.getPosition(0)),
							rolePath.text.length());
				for (String roleName : rolePath.text.split("\\.")) {
					roleName = roleName.trim();
					QonfigChildDef child = null;
					if (application.getRole() != null) {
						if (application.getRole().getType() != null)
							child = application.getRole().getType().getChild(roleName);
						else
							throw new QonfigInterpretationException("No such role '" + roleName + "' for parent style " + application, //
								rolePath.position == null ? null
									: new LocatedFilePosition(rolePath.fileLocation, rolePath.position.getPosition(0)),
									rolePath.text.length());
					}
					for (QonfigElementOrAddOn type : application.getTypes().values()) {
						if (child != null)
							break;
						child = type.getChild(roleName);
					}
					if (child == null)
						throw new QonfigInterpretationException("No such role '" + roleName + "' for parent style " + application, //
							rolePath.position == null ? null
								: new LocatedFilePosition(rolePath.fileLocation, rolePath.position.getPosition(0)),
								rolePath.text.length());
					theRoles.add(child);
					application = application.forChild(child);
				}
			}

			QonfigValue elName = session.attributes().get("element").get();
			if (elName != null && elName.text != null) {
				if (targetElement != null)
					throw new QonfigInterpretationException("element may only be specified within a style-sheet",
						reporting().at(elName.position).getFileLocation().getPosition(0), elName.position.length());
				QonfigElementOrAddOn el;
				try {
					el = session.getElement().getDocument().getDocToolkit().getElementOrAddOn(elName.text);
					if (el == null)
						throw new QonfigInterpretationException("No such element found: " + elName, //
							elName.position == null ? null : new LocatedFilePosition(elName.fileLocation, elName.position.getPosition(0)),
								elName.text.length());
				} catch (IllegalArgumentException e) {
					throw new QonfigInterpretationException(e.getMessage(),
						elName.position == null ? null : new LocatedFilePosition(elName.fileLocation, elName.position.getPosition(0)), //
							elName.text.length(), e);
				}
				theStyleElement = el;
				try {
					application = application.forType(el);
				} catch (IllegalArgumentException e) {
					throw new QonfigInterpretationException(e.getMessage(),
						elName.position == null ? null : new LocatedFilePosition(elName.fileLocation, elName.position.getPosition(0)), //
							elName.text.length(), e);
				}
			}

			ElementModelValue.Cache emvCache = session.getElementValueCache();
			theCondition = getAttributeExpression("if", session);
			if (theCondition != null) {
				QonfigAttributeDef.Declared priorityAttr = QuickTypeStyle.getPriorityAttr(getQonfigType().getDeclarer());
				theCondition = application.findModelValues(theCondition, new ArrayList<>(), getExpressoEnv().getModels(),
					priorityAttr.getDeclarer(), ancestorSS != null, emvCache, reporting());
				application = application.forCondition(theCondition, getExpressoEnv().getModels(), priorityAttr, ancestorSS != null,
					emvCache, reporting());
			}
			theApplication = application;

			QonfigValue attrName = session.attributes().get("attr").get();
			if (attrName != null) {
				if (parent != null && parent.getEffectiveAttribute() != null)
					throw new QonfigInterpretationException(
						"Cannot specify an attribute (" + attrName.text + ") if an ancestor style has (" + parent.getEffectiveAttribute()
						+ ")",
						attrName.position == null ? null : new LocatedFilePosition(attrName.fileLocation, attrName.position.getPosition(0)),
							attrName.text.length());
				QuickTypeStyle.TypeStyleSet styleTypeSet = session.get(STYLE_TYPE_SET, QuickTypeStyle.TypeStyleSet.class);
				if (styleTypeSet == null) {
					styleTypeSet = new QuickTypeStyle.TypeStyleSet();
					session.putGlobal(STYLE_TYPE_SET, styleTypeSet);
				}
				MultiInheritanceSet<QonfigElementOrAddOn> types;
				if (targetElement == null)
					types = application.getTypes();
				else {
					types = MultiInheritanceSet.create(QonfigElementOrAddOn::isAssignableFrom);
					types.add(targetElement.getType());
					types.addAll(targetElement.getInheritance().values());
				}
				theDeclaredAttribute = getAttribute(attrName, types, styleTypeSet, session.getWrapped().getInterpreter().getKnownToolkits(),
					session.reporting().at(attrName.position), getQonfigType().getDeclarer());
				theEffectiveAttribute = theDeclaredAttribute;
			} else if (parent != null)
				theEffectiveAttribute = parent.getEffectiveAttribute();
			else
				theEffectiveAttribute = null;

			theStyleValues.clear();
			theValue = getValueExpression(session);
			if (theValue != null && theValue.getExpression() != ObservableExpression.EMPTY) {
				if (theEffectiveAttribute == null)
					throw new QonfigInterpretationException("Cannot specify a style value without an attribute",
						theValue.getFilePosition().getPosition(0), theValue.length());
				QuickStyleSet styleSet = session.get(QuickStyleSet.STYLE_SET_SESSION_KEY, QuickStyleSet.class);
				theValue = theApplication.findModelValues(theValue, new HashSet<>(), getExpressoEnv().getModels(),
					getQonfigType().getDeclarer(), ancestorSS != null, emvCache, reporting());
				theStyleValues.add(new QuickStyleValue(ancestorSS, styleSet, theApplication, theEffectiveAttribute, theValue));
			}
			QonfigValue styleSetName = session.attributes().get("style-set").get();
			if (styleSetName != null) {
				if (styleSheet == null) {
					// Need to check for a weird condition to fire a better error message
					QonfigAddOn withStyleSheet = (QonfigAddOn) session.getType(ExWithStyleSheet.WITH_STYLE_SHEET);
					QonfigChildDef.Declared styleSheetRole = null;
					for (QonfigChildDef.Declared role : withStyleSheet.getDeclaredChildren().values()) {
						if (role.getName().equals(QuickStyleSheet.STYLE_SHEET)) {
							styleSheetRole = role;
							break;
						}
					}
					QonfigElement p, c;
					for (c = session.getElement(), p = c.getParent(); p != null
						&& !p.getInheritance().contains(withStyleSheet); c = p, p = p.getParent()) {
					}
					QonfigElement styleSheetEl = p == null ? null : p.getChildrenByRole().get(styleSheetRole).peekFirst();
					if (styleSheetEl != null) {
						// There actually is a style sheet that one might think would apply here,
						// but the order in which Expresso loads things means that it isn't available here.
						// This is a very confusing situation and needs a more descriptive error message to help the user understand
						// and resolve it
						throw new QonfigInterpretationException("No style-sheet available: Cannot refer to a style set.\n"
							+ "\tThe style sheet at " + styleSheetEl.getFilePosition().getPosition(0)
							+ " is not available due to loading order.\n"
							+ "\tE.g. elements in a <model> element with a <style-sheet> sibling won't see the style sheet"
							+ " because the models are loaded first.\n"
							+ "\tTry moving " + styleSheetEl.toLocatedString() + " up in the hierarchy or " + c.toLocatedString()
							+ " lower.",
							styleSetName.position == null ? null
								: new LocatedFilePosition(styleSetName.fileLocation, styleSetName.position.getPosition(0)),
								styleSetName.text.length());

					} else
						throw new QonfigInterpretationException("No style-sheet available: Cannot refer to a style set", //
							styleSetName.position == null ? null
								: new LocatedFilePosition(styleSetName.fileLocation, styleSetName.position.getPosition(0)),
								styleSetName.text.length());
				}
				if (theEffectiveAttribute != null)
					throw new QonfigInterpretationException(
						"Cannot refer to a style set when an attribute (" + theEffectiveAttribute + ") is specified", //
						styleSetName.position == null ? null
							: new LocatedFilePosition(styleSetName.fileLocation, styleSetName.position.getPosition(0)),
							styleSetName.text.length());
				try {
					theStyleSet = styleSheet.getStyleSet(styleSetName.text);
				} catch (IllegalArgumentException e) {
					throw new QonfigInterpretationException(e.getMessage(), //
						styleSetName.position == null ? null
							: new LocatedFilePosition(styleSetName.fileLocation, styleSetName.position.getPosition(0)),
							styleSetName.text.length());
				}
			} else
				theStyleSet = null;

			syncChildren(QuickStyleElement.Def.class, theChildren, session.forChildren("sub-style"));
		}

		private QuickStyleAttributeDef getAttribute(QonfigValue attrName, MultiInheritanceSet<QonfigElementOrAddOn> types,
			TypeStyleSet styleTypeSet, Set<QonfigToolkit> knownToolkits, ErrorReporting reporting, QonfigToolkit styleTK)
				throws QonfigInterpretationException {
			int dot = attrName.text.indexOf('.');
			Set<QuickStyleAttributeDef> attrs = new HashSet<>();
			if (dot >= 0) { // Type-qualified
				QonfigElementOrAddOn type;
				int colon = attrName.text.indexOf(':');
				if (colon >= 0 && colon < dot) { // Toolkit-qualified
					ToolkitSpec spec;
					try {
						spec = ToolkitSpec.parse(attrName.text.substring(0, colon));
					} catch (ParseException e) {
						throw new QonfigInterpretationException(
							"To qualify an attribute name with a toolkit, use the form 'ToolkitName v1.2:element-name.attr-name",
							new LocatedFilePosition(attrName.fileLocation, attrName.position.getPosition(0)), colon, e);
					}
					QonfigToolkit toolkit = null;
					for (QonfigToolkit tk : knownToolkits) {
						QonfigToolkit found = spec.find(tk);
						if (found != null) {
							if (toolkit != null && found != toolkit)
								throw new QonfigInterpretationException("Multiple toolkits found matching " + spec,
									new LocatedFilePosition(attrName.fileLocation, attrName.position.getPosition(0)), colon);
							toolkit = found;
						}
					}
					if (toolkit == null)
						throw new QonfigInterpretationException("No loaded toolkits found matching " + spec,
							new LocatedFilePosition(attrName.fileLocation, attrName.position.getPosition(0)), colon);
					String typeName = attrName.text.substring(colon + 1, dot);
					try {
						type = toolkit.getElementOrAddOn(typeName);
					} catch (IllegalArgumentException e) {
						throw new QonfigInterpretationException(e.getMessage(),
							new LocatedFilePosition(attrName.fileLocation, attrName.position.getPosition(colon + 1)), typeName.length(), e);
					}
					if (type == null)
						throw new QonfigInterpretationException("No such element or add-on '" + typeName + "' found in toolkit " + toolkit,
							new LocatedFilePosition(attrName.fileLocation, attrName.position.getPosition(colon + 1)), typeName.length());
				} else {
					String typeName = attrName.text.substring(0, dot);
					type = null;
					for (QonfigToolkit tk : knownToolkits) {
						QonfigElementOrAddOn found;
						try {
							found = tk.getElementOrAddOn(typeName);
						} catch (IllegalArgumentException e) {
							throw new QonfigInterpretationException(e.getMessage(),
								new LocatedFilePosition(attrName.fileLocation, attrName.position.getPosition(0)), typeName.length(), e);
						}
						if (found != null) {
							if (type != null && type != found)
								throw new QonfigInterpretationException(
									"Multiple elements/add-ons named '" + typeName + "' found in loaded toolkits",
									new LocatedFilePosition(attrName.fileLocation, attrName.position.getPosition(0)), typeName.length());
							type = found;
						}
					}
					if (type == null)
						throw new QonfigInterpretationException("No such element or add-on '" + typeName + "' found in loaded toolkits",
							new LocatedFilePosition(attrName.fileLocation, attrName.position.getPosition(colon + 1)), typeName.length());
				}
				QuickTypeStyle styled = styleTypeSet.getOrCompile(type, reporting, styleTK);
				if (styled == null)
					throw new QonfigInterpretationException("Element '" + attrName.text.substring(0, dot) + "' is not styled",
						new LocatedFilePosition(attrName.fileLocation, attrName.position.getPosition(0)), dot);
				attrs.addAll(styled.getAttributes(attrName.text.substring(dot + 1)));
				if (attrs.isEmpty())
					throw new QonfigInterpretationException("No such style attribute: " + attrName, //
						attrName.position == null ? null : new LocatedFilePosition(attrName.fileLocation, attrName.position.getPosition(0)),
							attrName.text.length());
				else if (attrs.size() > 1)
					throw new QonfigInterpretationException("Multiple style attributes found matching " + attrName + ": " + attrs, //
						attrName.position == null ? null : new LocatedFilePosition(attrName.fileLocation, attrName.position.getPosition(0)),
							attrName.text.length());
			} else {
				for (QonfigElementOrAddOn type : types.values()) {
					if (attrs.size() > 1)
						break;
					QuickTypeStyle styled = styleTypeSet.getOrCompile(type, reporting, styleTK);
					if (styled != null)
						attrs.addAll(styled.getAttributes(attrName.text));
				}
				if (attrs.isEmpty()) {
					if (types.isEmpty())
						throw new QonfigInterpretationException(
							"No element types for context, specify styles as 'element.style-name' (" + attrName + ")", //
							attrName.position == null ? null
								: new LocatedFilePosition(attrName.fileLocation, attrName.position.getPosition(0)),
								attrName.text.length());
					else
						throw new QonfigInterpretationException("No such style attribute: " + types + "." + attrName, //
							attrName.position == null ? null
								: new LocatedFilePosition(attrName.fileLocation, attrName.position.getPosition(0)),
								attrName.text.length());
				} else if (attrs.size() > 1)
					throw new QonfigInterpretationException(
						"Multiple style attributes found matching " + types + "." + attrName + ": " + attrs, //
						attrName.position == null ? null : new LocatedFilePosition(attrName.fileLocation, attrName.position.getPosition(0)),
							attrName.text.length());
			}
			return attrs.iterator().next();
		}

		private static class ToolkitSpec {
			final String name;
			final int major;
			final int minor;

			ToolkitSpec(String name, int major, int minor) {
				this.name = name;
				this.major = major;
				this.minor = minor;
			}

			QonfigToolkit find(QonfigToolkit toolkit) {
				if (name.equals(toolkit.getName())//
					&& (major < 0 || major == toolkit.getMajorVersion())//
					&& (minor < 0 || minor == toolkit.getMinorVersion()))
					return toolkit;
				for (QonfigToolkit dep : toolkit.getDependencies().values()) {
					QonfigToolkit found = find(dep);
					if (found != null)
						return found;
				}
				return null;
			}

			public static ToolkitSpec parse(CharSequence text) throws ParseException {
				int space = -1;
				for (int c = 0; c < text.length(); c++) {
					if (Character.isWhitespace(text.charAt(c))) {
						space = c;
						break;
					}
				}
				if (space < 0)
					return new ToolkitSpec(text.toString(), -1, -1);

				String name = text.subSequence(0, space).toString();
				int start = space + 1;
				while (start < text.length() && Character.isWhitespace(text.charAt(start)))
					start++;
				if (start < text.length() && (text.charAt(start) == 'v' || text.charAt(start) == 'V'))
					start++;
				if (start == text.length())
					throw new ParseException(
						"When specifying a toolkit-qualified style attribute, either have no spaces or the space should separate the toolkit name from its version",
						name.length());
				int major = 0;
				int majorStart = start;
				while (start < text.length() && text.charAt(start) >= '0' && text.charAt(start) <= '9') {
					major = major * 10 + text.charAt(start) - '0';
					if (major < 0)
						throw new ParseException("Major version is too large", majorStart);
					start++;
				}
				if (start == majorStart)
					throw new ParseException("Major version expected", majorStart);
				if (start == text.length())
					return new ToolkitSpec(name, major, -1);
				if (text.charAt(start) != '.')
					throw new ParseException("'.' separator expected between major an minor versions", start);
				start++;
				int minor = 0;
				int minorStart = start;
				while (start < text.length() && text.charAt(start) >= '0' && text.charAt(start) <= '9') {
					minor = minor * 10 + text.charAt(start) - '0';
					if (major < 0)
						throw new ParseException("Minor version is too large", minorStart);
					start++;
				}
				if (start == minorStart)
					throw new ParseException("Minor version expected", minorStart);
				while (start < text.length() && Character.isWhitespace(text.charAt(start)))
					start++;
				if (start != text.length())
					throw new ParseException("Extra information prohibited after version specification", start);
				return new ToolkitSpec(name, major, minor);
			}
		}

		/**
		 * @param parent The parent element for the interpreted style
		 * @return The interpreted style
		 */
		public Interpreted<?> interpret(ExElement.Interpreted<?> parent) {
			return new Interpreted<>(this, parent);
		}
	}

	/**
	 * Interpretation for a {@link QuickStyledElement}
	 *
	 * @param <T> The type of the style's attribute (if any)
	 */
	public static class Interpreted<T> extends ExElement.Interpreted.Abstract<QuickStyleElement<T>> {
		private QuickStyleAttribute<T> theDeclaredAttribute;
		private QuickStyleAttribute<T> theEffectiveAttribute;
		private InterpretedValueSynth<SettableValue<?>, SettableValue<Boolean>> theCondition;
		private InterpretedValueSynth<SettableValue<?>, SettableValue<T>> theValue;
		private final List<Interpreted<?>> theChildren;

		Interpreted(Def definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
			theChildren = new ArrayList<>();
		}

		@Override
		public Def getDefinition() {
			return (Def) super.getDefinition();
		}

		/** @return The style attribute declared by this element */
		public QuickStyleAttribute<T> getDeclaredAttribute() {
			return theDeclaredAttribute;
		}

		/** @return The style attribute that this style is for, if any */
		public QuickStyleAttribute<T> getEffectiveAttribute() {
			return theEffectiveAttribute;
		}

		/** @return A condition that must be true for this style to be effective */
		public InterpretedValueSynth<SettableValue<?>, SettableValue<Boolean>> getCondition() {
			return theCondition;
		}

		/** @return The expression containing the value for the style */
		public InterpretedValueSynth<SettableValue<?>, SettableValue<T>> getValue() {
			return theValue;
		}

		/** @return All sub-styles on this element */
		public List<Interpreted<?>> getChildren() {
			return Collections.unmodifiableList(theChildren);
		}

		/**
		 * Initializes or updates this style
		 *
		 * @param env The expresso environment for interpreting expressions
		 * @throws ExpressoInterpretationException If this style could not be interpreted
		 */
		public void updateStyle(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
			update(env);
		}

		@Override
		protected void doUpdate(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
			super.doUpdate(env);

			QuickInterpretedStyleCache cache = QuickInterpretedStyleCache.get(env);
			theDeclaredAttribute = (QuickStyleAttribute<T>) cache.getAttribute(getDefinition().getDeclaredAttribute(), env);
			theEffectiveAttribute = (QuickStyleAttribute<T>) cache.getAttribute(getDefinition().getEffectiveAttribute(), env);

			theCondition = interpret(getDefinition().getCondition(), ModelTypes.Value.BOOLEAN);
			if (getDefinition().getValue() != null && getDefinition().getValue().getExpression() != ObservableExpression.EMPTY)
				theValue = interpret(getDefinition().getValue(), ModelTypes.Value.forType(getEffectiveAttribute().getType()));
			else
				theValue = null;
			syncChildren(getDefinition().getChildren(), theChildren, def -> def.interpret(this), (i, sEnv) -> i.updateStyle(sEnv));
		}

		/** @return The style element */
		public QuickStyleElement<T> create() {
			return new QuickStyleElement<>(getIdentity());
		}
	}

	private ModelValueInstantiator<SettableValue<Boolean>> theConditionInstantiator;
	private ModelValueInstantiator<SettableValue<T>> theValueInstantiator;
	private final SettableValue<SettableValue<Boolean>> theCondition;
	private SettableValue<SettableValue<T>> theValue;
	private final List<QuickStyleElement<?>> theChildren;

	QuickStyleElement(Object id) {
		super(id);
		theCondition = SettableValue
			.build(TypeTokens.get().keyFor(SettableValue.class).<SettableValue<Boolean>> parameterized(boolean.class)).build();
		theChildren = new ArrayList<>();
	}

	/** @return A condition that must be true for this style to be effective */
	public SettableValue<Boolean> getCondition() {
		return SettableValue.flatten(theCondition, () -> true);
	}

	/** @return The value for the style */
	public SettableValue<T> getValue() {
		if (theValue == null)
			return (SettableValue<T>) SettableValue.of(Object.class, null, "Unsettable");
		else
			return SettableValue.flatten(theValue);
	}

	/** @return All sub-styles on this element */
	public List<QuickStyleElement<?>> getChildren() {
		return Collections.unmodifiableList(theChildren);
	}

	@Override
	protected void doUpdate(ExElement.Interpreted<?> interpreted) throws ModelInstantiationException {
		super.doUpdate(interpreted);
		Interpreted<T> myInterpreted = (Interpreted<T>) interpreted;
		QuickStyleAttribute<T> attr = myInterpreted.getEffectiveAttribute();
		if (attr == null || myInterpreted.getValue() == null)
			theValue = null;
		else
			theValue = SettableValue.build(TypeTokens.get().keyFor(SettableValue.class).<SettableValue<T>> parameterized(attr.getType()))
			.build();
		theConditionInstantiator = myInterpreted.getCondition() == null ? null : myInterpreted.getCondition().instantiate();
		if (theValue != null)
			theValueInstantiator = myInterpreted.getValue().instantiate();
		CollectionUtils.synchronize(theChildren, myInterpreted.getChildren(), (inst, interp) -> inst.getIdentity() == interp.getIdentity())//
		.<ModelInstantiationException> simpleX(interp -> interp.create())//
		.onRightX(el -> el.getLeftValue().update(el.getRightValue(), this))
		.onCommonX(el -> el.getLeftValue().update(el.getRightValue(), this)).adjust();
	}

	@Override
	public void instantiated() throws ModelInstantiationException {
		super.instantiated();

		if (theConditionInstantiator != null)
			theConditionInstantiator.instantiate();
		if (theValueInstantiator != null)
			theValueInstantiator.instantiate();

		for (QuickStyleElement<?> subStyle : theChildren)
			subStyle.instantiated();
	}

	@Override
	protected void doInstantiate(ModelSetInstance myModels) throws ModelInstantiationException {
		super.doInstantiate(myModels);
		theCondition.set(theConditionInstantiator == null ? null : theConditionInstantiator.get(myModels), null);
		if (theValue != null)
			theValue.set(theValueInstantiator.get(myModels), null);
		for (QuickStyleElement<?> child : theChildren)
			child.instantiate(myModels);
	}
}
