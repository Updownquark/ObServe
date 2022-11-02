package org.observe.quick.style;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.observe.expresso.ExpressoQIS;
import org.observe.expresso.ObservableExpression;
import org.observe.expresso.ObservableModelSet;
import org.qommons.IdentityKey;
import org.qommons.QommonsUtils;
import org.qommons.Version;
import org.qommons.config.DefaultQonfigParser;
import org.qommons.config.QommonsConfig;
import org.qommons.config.QonfigAddOn;
import org.qommons.config.QonfigChildDef;
import org.qommons.config.QonfigDocument;
import org.qommons.config.QonfigElement;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretation;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.config.QonfigInterpreterCore.Builder;
import org.qommons.config.QonfigInterpreterCore.CoreSession;
import org.qommons.config.QonfigParseException;
import org.qommons.config.QonfigToolkit;
import org.qommons.config.SpecialSession;

public class QuickStyle implements QonfigInterpretation {
	public static final String TOOLKIT_NAME = "Quick-Style";
	public static final String STYLE_NAME = "quick-style-name";
	public static final String STYLE_APPLICATION = "quick-parent-style-application";
	public static final String STYLE_ATTRIBUTE = "quick-style-attribute";
	public static final String STYLE_SHEET_REF = "quick-style-sheet-ref";
	public static final String STYLE_MODEL_VALUE_ELEMENT = "style-model-value";
	public static final String EXPRESSO_DEPENDENCY = "expresso";

	public static abstract class StyleValues extends AbstractList<QuickStyleValue<?>> {
		private static final ThreadLocal<LinkedHashSet<IdentityKey<StyleValues>>> STACK = ThreadLocal.withInitial(LinkedHashSet::new);

		private final String theName;
		private List<QuickStyleValue<?>> theValues;

		StyleValues(String name) {
			theName = name;
		}

		public StyleValues init() throws QonfigInterpretationException {
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
				throw new QonfigInterpretationException(str.toString());
			}
			try {
				theValues = get();
			} finally {
				stack.remove(new IdentityKey<>(this));
			}
			return this;
		}

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
		StyleValueApplication application = session.get(STYLE_APPLICATION, StyleValueApplication.class);
		if (application == null)
			application = StyleValueApplication.ALL;
		QuickStyleAttribute<?> attr = session.get(STYLE_ATTRIBUTE, QuickStyleAttribute.class);
		QonfigElement element = (QonfigElement) session.get(StyleQIS.STYLE_ELEMENT);
		modifyForStyle(session);

		String rolePath = session.getAttributeText("child");
		if (rolePath != null) {
			if (application == null)
				throw new QonfigInterpretationException("Cannot specify a style role without a type above it");
			for (String roleName : rolePath.split("\\.")) {
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
					throw new QonfigInterpretationException("No such role '" + roleName + "' for parent style " + application);
				application = application.forChild(child);
			}
		}
		String elName = session.getAttributeText("element");
		if (elName != null) {
			QonfigElementOrAddOn el;
			try {
				el = session.getElement().getDocument().getDocToolkit().getElementOrAddOn(elName);
				if (el == null)
					throw new QonfigInterpretationException("No such element found: " + elName);
			} catch (IllegalArgumentException e) {
				throw new QonfigInterpretationException(e.getMessage());
			}
			application = application.forType(el);
		}
		ObservableExpression newCondition = exS.getAttributeExpression("condition");
		if (newCondition != null) {
			application = application.forCondition(newCondition, exS.getExpressoEnv(), QuickStyleType.getPriorityAttr(theToolkit));
		}
		session.put(STYLE_APPLICATION, application);

		String attrName = session.getAttributeText("attr");
		if (attrName != null) {
			if (attr != null)
				throw new QonfigInterpretationException(
					"Cannot specify an attribute (" + attrName + ") if an ancestor style has (" + attr + ")");

			Set<QuickStyleAttribute<?>> attrs = new HashSet<>();
			if (element != null) {
				QuickStyleType styled = QuickStyleType.of(element.getType(), exS, theToolkit);
				if (styled != null)
					attrs.addAll(QuickStyleType.of(element.getType(), exS, theToolkit).getAttributes(attrName));
				for (QonfigAddOn inh : element.getInheritance().values()) {
					if (attrs.size() > 1)
						break;
					styled = QuickStyleType.of(inh, exS, theToolkit);
					if (styled != null)
						attrs.addAll(styled.getAttributes(attrName));
				}
			} else {
				for (QonfigElementOrAddOn type : application.getTypes().values()) {
					if (attrs.size() > 1)
						break;
					QuickStyleType styled = QuickStyleType.of(type, exS, theToolkit);
					if (styled != null)
						attrs.addAll(styled.getAttributes(attrName));
				}
			}
			if (attrs.isEmpty())
				throw new QonfigInterpretationException("No such style attribute: '" + attrName + "'");
			else if (attrs.size() > 1)
				throw new QonfigInterpretationException("Multiple style attributes found matching '" + attrName + "'");
			attr = attrs.iterator().next();
			session.put(STYLE_ATTRIBUTE, attr);
		}
		ObservableExpression value = exS.getValueExpression();
		if ((value != null && value != ObservableExpression.EMPTY) && attr == null)
			throw new QonfigInterpretationException("Cannot specify a style value without an attribute");
		String styleSetName = session.getAttributeText("style-set");
		List<QuickStyleValue<?>> styleSetRef;
		if (styleSetName != null) {
			if (attr != null)
				throw new QonfigInterpretationException("Cannot refer to a style set when an attribute is specified");
			try {
				styleSetRef = styleSheet.getStyleSet(styleSetName);
			} catch (IllegalArgumentException e) {
				throw new QonfigInterpretationException(e.getMessage());
			}
			if (styleSetRef instanceof StyleValues)
				((StyleValues) styleSetRef).init();
		} else
			styleSetRef = null;
		List<StyleValues> subStyles = session.interpretChildren("sub-style", StyleValues.class);
		for (StyleValues subStyle : subStyles)
			subStyle.init();

		StyleValueApplication theApplication = application;
		QuickStyleAttribute<?> theAttr = attr;
		return new StyleValues((String) session.get(STYLE_NAME)) {
			@Override
			protected List<QuickStyleValue<?>> get() throws QonfigInterpretationException {
				List<QuickStyleValue<?>> values = new ArrayList<>();
				if (value != null && value != ObservableExpression.EMPTY)
					values.add(new QuickStyleValue<>(styleSheet, theApplication, theAttr, value, exS.getExpressoEnv()));
				if (styleSetRef != null)
					values.addAll(styleSetRef);
				for (StyleValues child : subStyles)
					values.addAll(child);
				return values;
			}
		};
	}

	private static String printElOptions(Collection<QonfigElementOrAddOn> roleEls) {
		if (roleEls.size() == 1)
			return roleEls.iterator().next().toString();
		StringBuilder str = new StringBuilder().append('(');
		boolean first = true;
		for (QonfigElementOrAddOn el : roleEls) {
			if (first)
				first = false;
			else
				str.append('|');
			str.append(el);
		}
		return str.append(')').toString();
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
			String address = sse.getAttributeText("ref");
			URL ref;
			try {
				String urlStr = QommonsConfig.resolve(address, session.getElement().getDocument().getLocation());
				ref = new URL(urlStr);
			} catch (IOException e) {
				throw new QonfigInterpretationException("Bad style-sheet reference: " + sse.getAttributeText("ref"), e);
			}
			QonfigDocument ssDoc;
			try (InputStream in = new BufferedInputStream(ref.openStream())) {
				ssDoc = parser.parseDocument(ref.toString(), in);
			} catch (IOException e) {
				throw new QonfigInterpretationException("Could not access style-sheet reference " + ref, e);
			} catch (QonfigParseException e) {
				throw new QonfigInterpretationException("Malformed style-sheet reference " + ref, e);
			}
			if (!session.getType().getDeclarer().getElement("style-sheet").isAssignableFrom(ssDoc.getRoot().getType()))
				throw new QonfigInterpretationException(
					"Style-sheet reference does not parse to a style-sheet (" + ssDoc.getRoot().getType() + "): " + ref);
			StyleQIS importSession = session.intepretRoot(ssDoc.getRoot())//
				.put(STYLE_SHEET_REF, ref);
			importSession.as(ExpressoQIS.class)//
				.setModels(ObservableModelSet.build(address, exS.getExpressoEnv().getModels().getNameChecker()).build(),
				exS.getExpressoEnv().getClassView());
			modifyForStyle(session);
			QuickStyleSheet imported = importSession.interpret(QuickStyleSheet.class);
			imports.put(name, imported);
		}

		// Next, compile style-sets
		Map<String, List<QuickStyleValue<?>>> styleSets = new LinkedHashMap<>();
		for (StyleQIS styleSetEl : session.forChildren("style-set")) {
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
		for (StyleValues sv : session.interpretChildren("style", StyleValues.class)) {
			sv.init();
			values.addAll(sv);
		}

		// Replace the StyleValues instances in the styleSets map with regular lists. Don't keep that silly type around.
		// This also forces parsing of all the values if they weren't referred to internally.
		for (Map.Entry<String, List<QuickStyleValue<?>>> ss : styleSets.entrySet()) {
			((StyleValues) ss.getValue()).init();
			ss.setValue(QommonsUtils.unmodifiableCopy(ss.getValue()));
		}
		return styleSheet;
	}
}
