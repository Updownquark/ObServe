package org.observe.quick.style;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.text.ParseException;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.observe.expresso.CompiledExpression;
import org.observe.expresso.DynamicModelValue;
import org.observe.expresso.Expresso;
import org.observe.expresso.ExpressoQIS;
import org.observe.expresso.LocatedExpression;
import org.observe.expresso.ObservableExpression;
import org.observe.expresso.ObservableModelSet;
import org.observe.util.TypeTokens;
import org.qommons.IdentityKey;
import org.qommons.QommonsUtils;
import org.qommons.Version;
import org.qommons.collect.BetterList;
import org.qommons.config.*;
import org.qommons.config.QonfigElement.QonfigValue;
import org.qommons.config.QonfigInterpreterCore.Builder;
import org.qommons.config.QonfigInterpreterCore.CoreSession;
import org.qommons.config.QonfigInterpreterCore.QonfigValueModifier;
import org.qommons.io.LocatedFilePosition;
import org.qommons.io.SimpleXMLParser;

/** Interpretation for the Quick-Style toolkit */
public class QuickStyleInterpretation implements QonfigInterpretation {
	static final String STYLE_NAME = "quick-style-name";
	static final String STYLE_ELEMENT = "quick-style-element";
	static final String STYLE_APPLICATION = "quick-parent-style-application";
	static final String STYLE_ATTRIBUTE = "quick-style-attribute";
	static final String STYLE_SHEET_REF = "quick-style-sheet-ref";
	static final String MODEL_ELEMENT_NAME = "MODEL$ELEMENT";

	/** Interpretation type for interpreting style information */
	public static abstract class StyleValues extends AbstractList<QuickStyleValue<?>> {
		private static final ThreadLocal<LinkedHashSet<IdentityKey<StyleValues>>> STACK = ThreadLocal.withInitial(LinkedHashSet::new);

		private final String theName;
		private List<QuickStyleValue<?>> theValues;

		StyleValues(String name) {
			theName = name;
		}

		/**
		 * Initializes this style information, performing preliminary interpretation
		 *
		 * @param element The element for error throwing
		 * @return This object
		 * @throws QonfigInterpretationException If an error exists in this interpreted style information
		 */
		protected StyleValues init(QonfigElement element) throws QonfigInterpretationException {
			if (theValues != null)
				return this;
			LinkedHashSet<IdentityKey<StyleValues>> stack = STACK.get();
			if (!stack.add(new IdentityKey<>(this))) {
				StringBuilder str = new StringBuilder("Style sheet cycle detected:");
				for (IdentityKey<StyleValues> se : stack) {
					if (se.value.theName != null)
						str.append(se.value.theName).append("->");
				}
				str.append(theName);
				throw new QonfigInterpretationException(str.toString(), element.getPositionInFile(), 0);
			}
			try {
				theValues = get();
			} finally {
				stack.remove(new IdentityKey<>(this));
			}
			return this;
		}

		/**
		 * @return All style values interpreted in this structure
		 * @throws QonfigInterpretationException If an error exists in this interpreted style information
		 */
		protected abstract List<QuickStyleValue<?>> get() throws QonfigInterpretationException;

		@Override
		public QuickStyleValue<?> get(int index) {
			if (theValues == null)
				throw new IllegalStateException("Not initialized");
			return theValues.get(index);
		}

		@Override
		public int size() {
			if (theValues == null)
				throw new IllegalStateException("Not initialized");
			return theValues.size();
		}
	}

	private QonfigToolkit theToolkit;
	private QonfigAttributeDef.Declared thePriorityAttr;

	@Override
	public String getToolkitName() {
		return StyleSessionImplV0_1.NAME;
	}

	@Override
	public Version getVersion() {
		return StyleSessionImplV0_1.VERSION;
	}

	@Override
	public void init(QonfigToolkit toolkit) {
		theToolkit = toolkit;
		thePriorityAttr = QuickTypeStyle.getPriorityAttr(theToolkit);
	}

	@Override
	public Set<Class<? extends SpecialSession<?>>> getExpectedAPIs() {
		return QommonsUtils.unmodifiableDistinctCopy(ExpressoQIS.class, StyleQIS.class);
	}

	static StyleQIS wrap(CoreSession session) throws QonfigInterpretationException {
		return session.as(StyleQIS.class);
	}

	@Override
	public Builder configureInterpreter(Builder interpreter) {
		interpreter//
		.modifyWith("with-style-sheet", Object.class, new QonfigValueModifier<Object>() {
			@Override
			public Object prepareSession(CoreSession session) throws QonfigInterpretationException {
				QuickStyleSheet styleSheet = session.interpretChildren("style-sheet", QuickStyleSheet.class).peekFirst();
				session.as(StyleQIS.class).setStyleSheet(styleSheet);
				return null;
			}

			@Override
			public Object modifyValue(Object value, CoreSession session, Object prepared) throws QonfigInterpretationException {
				return value;
			}
		})//
		.modifyWith("styled", Object.class, new Expresso.ElementModelAugmentation<Object>() {
			@Override
			public void augmentElementModel(ExpressoQIS session, ObservableModelSet.Builder builder)
				throws QonfigInterpretationException {
				builder.withTagValue(StyleQIS.STYLED_ELEMENT_TAG, session.getElement());
				QuickCompiledStyle parentStyle = session.get(StyleQIS.STYLE_PROP, QuickCompiledStyle.class);
				QuickStyleSheet styleSheet = session.as(StyleQIS.class).getStyleSheet();
				// Parse style values, if any
				session.put(STYLE_ELEMENT, session.getElement());
				List<QuickStyleValue<?>> declared = null;
				List<QuickStyleElement.Def> styleElements = null;
				for (ExpressoQIS svSession : session.forChildren("style")) {
					StyleValues sv = svSession.interpret(StyleValues.class);
					sv.init(session.getElement());
					if (declared == null) {
						declared = new ArrayList<>();
						styleElements = new ArrayList<>();
					}
					declared.addAll(sv);
					styleElements.add((QuickStyleElement.Def) svSession.as(StyleQIS.class).getStyleElement());
				}
				declared = QommonsUtils.unmodifiableCopy(declared);
				styleElements = QommonsUtils.unmodifiableCopy(styleElements);

				DynamicModelValue.satisfyDynamicValue(MODEL_ELEMENT_NAME, session.getExpressoEnv().getModels(), //
					ObservableModelSet.CompiledModelValue.literal(TypeTokens.get().of(QonfigElement.class), session.getElement(),
						MODEL_ELEMENT_NAME));
				StyleQIS styleSession = session.as(StyleQIS.class);
				session.put(StyleQIS.STYLE_PROP,
					new QuickCompiledStyle.Default(styleSession.getStyleTypes(), declared, parentStyle, styleSheet,
						session.getElement(), session, theToolkit, new HashMap<>(), styleElements));
			}
		})//
		.createWith("style", StyleValues.class, session -> interpretStyle(wrap(session)))//
		.createWith("style-sheet", QuickStyleSheet.class, session -> interpretStyleSheet(wrap(session)))//
		;
		return interpreter;
	}

	private void modifyForStyle(StyleQIS session) throws QonfigInterpretationException {
		ExpressoQIS exS = session.as(ExpressoQIS.class);
		exS.setExpressoEnv(exS.getExpressoEnv().with(null, null)// Create a copy
			.withNonStructuredParser(double.class, new FontValueParser()));
	}

	private StyleValues interpretStyle(StyleQIS session) throws QonfigInterpretationException {
		ExpressoQIS exS = session.as(ExpressoQIS.class);
		QuickStyleSheet styleSheet = session.getStyleSheet();
		StyleApplicationDef application = session.get(STYLE_APPLICATION, StyleApplicationDef.class);
		if (application == null)
			application = StyleApplicationDef.ALL;
		QuickStyleAttribute<?> attr = session.get(STYLE_ATTRIBUTE, QuickStyleAttribute.class);
		QonfigElement element = (QonfigElement) session.get(STYLE_ELEMENT);
		modifyForStyle(session);

		QonfigElementOrAddOn declaredType = null;
		QonfigChildDef declaredRole = null;
		CompiledExpression declaredCondition = null;
		QuickStyleSet styleSetRef;
		QuickStyleAttribute<?> declaredAttr = null;
		CompiledExpression value;

		QonfigValue rolePath = session.getAttributeQV("child");
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
						rolePath.position == null ? null : new LocatedFilePosition(rolePath.fileLocation, rolePath.position.getPosition(0)),
							rolePath.text.length());
				declaredRole = child;
				application = application.forChild(child);
			}
		}
		QonfigValue elName = session.getAttributeQV("element");
		if (elName != null && elName.text != null) {
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
			declaredType = el;
			try {
				application = application.forType(el);
			} catch (IllegalArgumentException e) {
				throw new QonfigInterpretationException(e.getMessage(),
					elName.position == null ? null : new LocatedFilePosition(elName.fileLocation, elName.position.getPosition(0)), //
						elName.text.length(), e);
			}
		}
		DynamicModelValue.Cache dmvCache = exS.getDynamicValueCache();
		declaredCondition = exS.getAttributeExpression("condition");
		if (declaredCondition != null)
			application = application.forCondition(declaredCondition, exS.getExpressoEnv(), thePriorityAttr, styleSheet != null, dmvCache);
		session.put(STYLE_APPLICATION, application);

		QonfigValue attrName = session.getAttributeQV("attr");
		if (attrName != null) {
			if (attr != null)
				throw new QonfigInterpretationException(
					"Cannot specify an attribute (" + attrName.text + ") if an ancestor style has (" + attr + ")",
					attrName.position == null ? null : new LocatedFilePosition(attrName.fileLocation, attrName.position.getPosition(0)),
						attrName.text.length());

			int dot = attrName.text.indexOf('.');
			Set<QuickStyleAttribute<?>> attrs = new HashSet<>();
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
					for (QonfigToolkit tk : session.getWrapped().getInterpreter().getKnownToolkits()) {
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
					for (QonfigToolkit tk : session.getWrapped().getInterpreter().getKnownToolkits()) {
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
				QuickTypeStyle styled = session.getStyleTypes().getOrCompile(type, session, theToolkit);
				if (styled == null)
					throw new QonfigInterpretationException("Element '" + attrName.text.substring(0, dot) + "' is not styled",
						new LocatedFilePosition(attrName.fileLocation, attrName.position.getPosition(0)), dot);
				attrs.addAll(styled.getAttributes(attrName.text.substring(dot + 1)));
				if (attrs.isEmpty())
					throw new QonfigInterpretationException("No such style attribute: " + attrName, //
						attrName.position == null ? null : new LocatedFilePosition(attrName.fileLocation, attrName.position.getPosition(0)),
							attrName.text.length());
				else if (attrs.size() > 1)
					throw new QonfigInterpretationException("Multiple style attributes found matching " + attrName, //
						attrName.position == null ? null : new LocatedFilePosition(attrName.fileLocation, attrName.position.getPosition(0)),
							attrName.text.length());
			} else if (element != null) {
				QuickTypeStyle styled = session.getStyleTypes().getOrCompile(element.getType(), exS, theToolkit);
				if (styled != null)
					attrs.addAll(styled.getAttributes(attrName.text));
				for (QonfigAddOn inh : element.getInheritance().values()) {
					if (attrs.size() > 1)
						break;
					styled = session.getStyleTypes().getOrCompile(inh, exS, theToolkit);
					if (styled != null)
						attrs.addAll(styled.getAttributes(attrName.text));
				}
				if (attrs.isEmpty())
					throw new QonfigInterpretationException("No such style attribute: " + element + "." + attrName, //
						attrName.position == null ? null : new LocatedFilePosition(attrName.fileLocation, attrName.position.getPosition(0)),
							attrName.text.length());
				else if (attrs.size() > 1)
					throw new QonfigInterpretationException("Multiple style attributes found matching " + element + "." + attrName, //
						attrName.position == null ? null : new LocatedFilePosition(attrName.fileLocation, attrName.position.getPosition(0)),
							attrName.text.length());
			} else {
				for (QonfigElementOrAddOn type : application.getTypes().values()) {
					if (attrs.size() > 1)
						break;
					QuickTypeStyle styled = session.getStyleTypes().getOrCompile(type, exS, theToolkit);
					if (styled != null)
						attrs.addAll(styled.getAttributes(attrName.text));
				}
				if (attrs.isEmpty())
					throw new QonfigInterpretationException("No such style attribute: " + application.getTypes() + "." + attrName, //
						attrName.position == null ? null : new LocatedFilePosition(attrName.fileLocation, attrName.position.getPosition(0)),
							attrName.text.length());
				else if (attrs.size() > 1)
					throw new QonfigInterpretationException(
						"Multiple style attributes found matching " + application.getTypes() + "." + attrName, //
						attrName.position == null ? null : new LocatedFilePosition(attrName.fileLocation, attrName.position.getPosition(0)),
							attrName.text.length());
			}
			attr = attrs.iterator().next();
			declaredAttr = attr;
			session.put(STYLE_ATTRIBUTE, attr);
		}
		value = exS.getValueExpression();
		if ((value != null && value.getExpression() != ObservableExpression.EMPTY) && attr == null)
			throw new QonfigInterpretationException("Cannot specify a style value without an attribute",
				value.getFilePosition().getPosition(0), value.length());
		QonfigValue styleSetName = session.getAttributeQV("style-set");
		if (styleSetName != null) {
			if (attr != null)
				throw new QonfigInterpretationException("Cannot refer to a style set when an attribute is specified", //
					styleSetName.position == null ? null
						: new LocatedFilePosition(styleSetName.fileLocation, styleSetName.position.getPosition(0)),
						styleSetName.text.length());
			try {
				styleSetRef = styleSheet.getStyleSet(styleSetName.text);
			} catch (IllegalArgumentException e) {
				throw new QonfigInterpretationException(e.getMessage(), //
					styleSetName.position == null ? null
						: new LocatedFilePosition(styleSetName.fileLocation, styleSetName.position.getPosition(0)),
						styleSetName.text.length());
			}
			// Should have been initialize already. Hopefully, because now I can't access the StyleValues to initialize it
			// if (styleSetRef instanceof StyleValues)
			// ((StyleValues) styleSetRef).init(session.getElement());
		} else
			styleSetRef = null;
		List<QuickStyleElement.Def> subStyleElements = new ArrayList<>();
		QuickStyleElement.Def styleElement = new QuickStyleElement.Def(session.getStyleElement(), session, declaredType,
			declaredRole, declaredCondition, styleSetRef, declaredAttr, attr, value, Collections.unmodifiableList(subStyleElements));
		session.setStyleElement(styleElement);
		List<StyleValues> subStyles = new ArrayList<>();
		for (StyleQIS subStyleEl : session.forChildren("sub-style")) {
			StyleValues subStyle = subStyleEl.interpret(StyleValues.class);
			subStyleElements.add((QuickStyleElement.Def) subStyleEl.getStyleElement());
			subStyle.init(subStyleEl.getElement());
			subStyles.add(subStyle);
		}

		StyleApplicationDef theApplication = application;
		QuickStyleAttribute<?> theAttr = attr;
		return new StyleValues((String) session.get(STYLE_NAME)) {
			@Override
			protected List<QuickStyleValue<?>> get() throws QonfigInterpretationException {
				List<QuickStyleValue<?>> values = new ArrayList<>();
				if (value != null && value.getExpression() != ObservableExpression.EMPTY) {
					Set<DynamicModelValue.Identity> mvs = new LinkedHashSet<>();
					LocatedExpression replacedValue = theApplication.findModelValues(value, mvs, exS.getExpressoEnv().getModels(),
						theToolkit, styleSheet != null, dmvCache);
					values.add(new QuickStyleValue<>(styleSheet, theApplication, theAttr, replacedValue));
				}
				if (styleSetRef != null) {
					for (QuickStyleValue<?> ssv : styleSetRef.getValues()) {
						ssv = ssv.when(theApplication);
						if (ssv.getApplication() == StyleApplicationDef.NONE)
							continue;
						values.add(ssv);
					}
				}
				for (StyleValues child : subStyles)
					values.addAll(child);
				return values;
			}
		};
	}

	private QuickStyleSheet interpretStyleSheet(StyleQIS session) throws QonfigInterpretationException {
		ExpressoQIS exS = session.as(ExpressoQIS.class);

		Map<String, QuickStyleSheet> imports = new LinkedHashMap<>();
		Map<String, QuickStyleSet> styleSets = new LinkedHashMap<>();
		List<QuickStyleValue<?>> values = new ArrayList<>();
		List<QuickStyleSheet.StyleSheetRef> importedRefs = new ArrayList<>();
		List<QuickStyleElement.Def> styleSheetElements = new ArrayList<>();
		QuickStyleSheet styleSheet = new QuickStyleSheet(session.getStyleElement(), session,
			(URL) session.get(STYLE_SHEET_REF), Collections.unmodifiableMap(styleSets), Collections.unmodifiableList(values),
			Collections.unmodifiableMap(imports), Collections.unmodifiableList(importedRefs),
			Collections.unmodifiableList(styleSheetElements));
		session.setStyleSheet(styleSheet);
		session.setStyleElement(styleSheet);

		// First import style sheets
		DefaultQonfigParser parser = null;
		for (StyleQIS sse : session.forChildren("style-sheet-ref")) {
			if (parser == null) {
				parser = new DefaultQonfigParser();
				for (QonfigToolkit tk : session.getElement().getDocument().getDocToolkit().getDependencies().values())
					parser.withToolkit(tk);
			}
			QonfigValue address = sse.getAttributeQV("ref");
			URL ref;
			try {
				String urlStr = QommonsConfig.resolve(address.text, session.getElement().getDocument().getLocation());
				ref = new URL(urlStr);
			} catch (IOException e) {
				throw new QonfigInterpretationException("Bad style-sheet reference: " + sse.getAttributeText("ref"),
					address.position == null ? null : new LocatedFilePosition(address.fileLocation, address.position.getPosition(0)), //
						address.text.length(), e);
			}
			QonfigDocument ssDoc;
			try (InputStream in = new BufferedInputStream(ref.openStream())) {
				ssDoc = parser.parseDocument(ref.toString(), in);
			} catch (IOException e) {
				throw new QonfigInterpretationException("Could not access style-sheet reference " + ref,
					address.position == null ? null : new LocatedFilePosition(address.fileLocation, address.position.getPosition(0)), //
						address.text.length(), e);
			} catch (SimpleXMLParser.XmlParseException e) {
				throw new QonfigInterpretationException("Could not parse style-sheet reference " + ref,
					address.position == null ? null : new LocatedFilePosition(address.fileLocation, address.position.getPosition(0)), //
						address.text.length(), e);
			} catch (QonfigParseException e) {
				throw new QonfigInterpretationException("Malformed style-sheet reference " + ref,
					address.position == null ? null : new LocatedFilePosition(address.fileLocation, address.position.getPosition(0)), //
						address.text.length(), e);
			}
			if (!session.getFocusType().getDeclarer().getElement("style-sheet").isAssignableFrom(ssDoc.getRoot().getType()))
				throw new QonfigInterpretationException(
					"Style-sheet reference does not parse to a style-sheet (" + ssDoc.getRoot().getType() + "): " + ref, //
					address.position == null ? null : new LocatedFilePosition(address.fileLocation, address.position.getPosition(0)),
						address.text.length());
			StyleQIS importSession = session.intepretRoot(ssDoc.getRoot())//
				.put(STYLE_SHEET_REF, ref);
			importSession.as(ExpressoQIS.class)//
			.setModels(ObservableModelSet.build(address.text, exS.getExpressoEnv().getModels().getNameChecker()).build(),
				exS.getExpressoEnv().getClassView());
			modifyForStyle(session);
			QuickStyleSheet imported = importSession.interpret(QuickStyleSheet.class);
			String name = sse.getAttributeText("name");
			QuickStyleSheet.StyleSheetRef ssr = new QuickStyleSheet.StyleSheetRef(styleSheet, sse, imported);
			importedRefs.add(ssr);
			imports.put(name, imported);
		}

		// Next, compile style-sets
		List<StyleQIS> styleSetEls = session.forChildren("style-set");
		Map<String, StyleValues> styleSetParsedValues = styleSetEls.isEmpty() ? null : new LinkedHashMap<>();
		Map<String, List<QuickStyleValue<?>>> styleSetValueLists = styleSetEls.isEmpty() ? null : new LinkedHashMap<>();
		for (StyleQIS styleSetEl : styleSetEls) {
			String name = styleSetEl.getAttributeText("name");
			styleSetEl.put(STYLE_NAME, name);
			List<QuickStyleValue<?>> thisSSVs = new ArrayList<>();
			styleSetValueLists.put(name, thisSSVs);
			List<QuickStyleElement.Def> styleSetElements = new ArrayList<>();
			QuickStyleSet styleSet = new QuickStyleSet(styleSheet, styleSetEl, name, Collections.unmodifiableList(thisSSVs),
				Collections.unmodifiableList(styleSetElements));
			styleSets.put(name, styleSet);
			styleSetEl.setStyleElement(styleSet);
			List<StyleQIS> styleValueEls = styleSetEl.forChildren("style");
			List<StyleValues> styleSetValues = new ArrayList<>(styleValueEls.size());
			for (StyleQIS styleValueEl : styleValueEls) {
				styleSetValues.add(styleValueEl.interpret(StyleValues.class));
				styleSetElements.add((QuickStyleElement.Def) styleValueEl.getStyleElement());
			}
			styleSetParsedValues.put(name, new StyleValues(name) {
				@Override
				protected List<QuickStyleValue<?>> get() throws QonfigInterpretationException {
					return BetterList.of(styleSetValues.stream(), sv -> sv.get().stream());
				}
			});
		}

		// Now compile the style-sheet styles
		String name = session.getElement().getDocument().getLocation();
		int slash = name.lastIndexOf('/');
		if (slash >= 0)
			name = name.substring(slash + 1);
		session.put(STYLE_NAME, name);
		for (StyleQIS subStyleEl : session.forChildren("style")) {
			StyleValues subStyle = subStyleEl.interpret(StyleValues.class);
			styleSheetElements.add((QuickStyleElement.Def) subStyleEl.getStyleElement());
			subStyle.init(subStyleEl.getElement());
			values.addAll(subStyle.get());
		}

		// Replace the StyleValues instances in the styleSets map with regular lists. Don't keep that silly type around.
		// This also forces parsing of all the values if they weren't referred to internally.
		for (Map.Entry<String, QuickStyleSet> ss : styleSets.entrySet()) {
			StyleValues ssvs = styleSetParsedValues.get(ss.getKey());
			ssvs.init(ss.getValue().getElement());
			styleSetValueLists.remove(ss.getKey()).addAll(ssvs);
		}
		return styleSheet;
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
}
