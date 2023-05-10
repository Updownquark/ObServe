package org.observe.quick.style;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
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
				session.as(StyleQIS.class).setStyleSheet(styleSheet != null ? styleSheet : QuickStyleSheet.EMPTY);
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
				QuickStyleSheet styleSheet = session.get(StyleQIS.STYLE_SHEET_PROP, QuickStyleSheet.class);
				// Parse style values, if any
				session.put(STYLE_ELEMENT, session.getElement());
				List<QuickStyleValue<?>> declared = null;
				for (StyleValues sv : session.interpretChildren("style", StyleValues.class)) {
					sv.init(session.getElement());
					if (declared == null)
						declared = new ArrayList<>();
					declared.addAll(sv);
				}
				if (declared == null)
					declared = Collections.emptyList();
				Collections.sort(declared);

				// Create QuickInterpretedStyle and put into session
				ExpressoQIS exS = session.as(ExpressoQIS.class);
				DynamicModelValue.satisfyDynamicValue(MODEL_ELEMENT_NAME, exS.getExpressoEnv().getModels(), //
					ObservableModelSet.CompiledModelValue.literal(TypeTokens.get().of(QonfigElement.class), session.getElement(),
						MODEL_ELEMENT_NAME));
				session.put(StyleQIS.STYLE_PROP, new QuickCompiledStyle.Default(Collections.unmodifiableList(declared), parentStyle,
					styleSheet, session.getElement(), exS, theToolkit, new HashMap<>()));
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

		QonfigValue rolePath = session.getAttributeQV("child");
		if (rolePath != null && rolePath.value != null) { // Role path may be defaulted
			if (application == null)
				throw new QonfigInterpretationException("Cannot specify a style role without a type above it", //
					rolePath.position == null ? null : new LocatedFilePosition(rolePath.fileLocation, rolePath.position.getPosition(0)),
						rolePath.text.length());
			for (String roleName : rolePath.text.split("\\.")) {
				roleName = roleName.trim();
				QonfigChildDef child = null;
				if (application.getRole() != null)
					child = application.getRole().getType().getChild(roleName);
				for (QonfigElementOrAddOn type : application.getTypes().values()) {
					if (child != null)
						break;
					child = type.getChild(roleName);
				}
				if (child == null)
					throw new QonfigInterpretationException("No such role '" + roleName + "' for parent style " + application, //
						rolePath.position == null ? null : new LocatedFilePosition(rolePath.fileLocation, rolePath.position.getPosition(0)),
							rolePath.text.length());
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
			application = application.forType(el);
		}
		CompiledExpression newCondition = exS.getAttributeExpression("condition");
		if (newCondition != null)
			application = application.forCondition(newCondition, exS.getExpressoEnv(), thePriorityAttr, styleSheet != null);
		session.put(STYLE_APPLICATION, application);

		QonfigValue attrName = session.getAttributeQV("attr");
		if (attrName != null) {
			if (attr != null)
				throw new QonfigInterpretationException(
					"Cannot specify an attribute (" + attrName.text + ") if an ancestor style has (" + attr + ")",
					attrName.position == null ? null : new LocatedFilePosition(attrName.fileLocation, attrName.position.getPosition(0)),
						attrName.text.length());

			Set<QuickStyleAttribute<?>> attrs = new HashSet<>();
			if (element != null) {
				QuickTypeStyle styled = QuickTypeStyle.getOrCompile(element.getType(), exS, theToolkit);
				if (styled != null)
					attrs.addAll(QuickTypeStyle.getOrCompile(element.getType(), exS, theToolkit).getAttributes(attrName.text));
				for (QonfigAddOn inh : element.getInheritance().values()) {
					if (attrs.size() > 1)
						break;
					styled = QuickTypeStyle.getOrCompile(inh, exS, theToolkit);
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
					QuickTypeStyle styled = QuickTypeStyle.getOrCompile(type, exS, theToolkit);
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
			session.put(STYLE_ATTRIBUTE, attr);
		}
		CompiledExpression value = exS.getValueExpression();
		if ((value != null && value.getExpression() != ObservableExpression.EMPTY) && attr == null)
			throw new QonfigInterpretationException("Cannot specify a style value without an attribute", value.getFilePosition(),
				value.length());
		QonfigValue styleSetName = session.getAttributeQV("style-set");
		List<QuickStyleValue<?>> styleSetRef;
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
			if (styleSetRef instanceof StyleValues)
				((StyleValues) styleSetRef).init(session.getElement());
		} else
			styleSetRef = null;
		List<StyleValues> subStyles = new ArrayList<>();
		for (StyleQIS subStyleEl : session.forChildren("sub-style")) {
			StyleValues subStyle = subStyleEl.interpret(StyleValues.class);
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
						theToolkit, styleSheet != null);
					values.add(new QuickStyleValue<>(styleSheet, theApplication, theAttr, replacedValue));
				}
				if (styleSetRef != null)
					values.addAll(styleSetRef);
				for (StyleValues child : subStyles)
					values.addAll(child);
				return values;
			}
		};
	}

	private QuickStyleSheet interpretStyleSheet(StyleQIS session) throws QonfigInterpretationException {
		ExpressoQIS exS = session.as(ExpressoQIS.class);
		// First import style sheets
		Map<String, QuickStyleSheet> imports = new LinkedHashMap<>();
		DefaultQonfigParser parser = null;
		for (StyleQIS sse : session.forChildren("style-sheet-ref")) {
			String name = sse.getAttributeText("name");
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
			imports.put(name, imported);
		}

		// Next, compile style-sets
		Map<String, List<QuickStyleValue<?>>> styleSets = new LinkedHashMap<>();
		List<StyleQIS> styleSetEls = session.forChildren("style-set");
		for (StyleQIS styleSetEl : styleSetEls) {
			String name = styleSetEl.getAttributeText("name");
			styleSetEl.put(STYLE_NAME, name);
			styleSets.put(name, styleSetEl.interpretChildren("style", StyleValues.class).getFirst());
		}

		// Now compile the style-sheet styles
		String name = session.getElement().getDocument().getLocation();
		int slash = name.lastIndexOf('/');
		if (slash >= 0)
			name = name.substring(slash + 1);
		session.put(STYLE_NAME, name);
		List<QuickStyleValue<?>> values = new ArrayList<>();
		QuickStyleSheet styleSheet = new QuickStyleSheet((URL) session.get(STYLE_SHEET_REF), Collections.unmodifiableMap(styleSets),
			Collections.unmodifiableList(values), Collections.unmodifiableMap(imports));
		session.setStyleSheet(styleSheet);
		for (StyleQIS subStyleEl : session.forChildren("style")) {
			StyleValues subStyle = subStyleEl.interpret(StyleValues.class);
			subStyle.init(subStyleEl.getElement());
			values.addAll(subStyle.get());
		}

		// Replace the StyleValues instances in the styleSets map with regular lists. Don't keep that silly type around.
		// This also forces parsing of all the values if they weren't referred to internally.
		int sse = 0;
		for (Map.Entry<String, List<QuickStyleValue<?>>> ss : styleSets.entrySet()) {
			((StyleValues) ss.getValue()).init(styleSetEls.get(sse).getElement());
			ss.setValue(QommonsUtils.unmodifiableCopy(ss.getValue()));
			sse++;
		}
		return styleSheet;
	}
}