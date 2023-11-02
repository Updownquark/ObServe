package org.observe.quick.style;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.observe.expresso.CompiledExpressoEnv;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.InterpretedExpressoEnv;
import org.observe.expresso.ObservableModelSet;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExElementTraceable;
import org.observe.expresso.qonfig.ExWithRequiredModels;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.expresso.qonfig.QonfigAttributeGetter;
import org.observe.expresso.qonfig.QonfigChildGetter;
import org.qommons.collect.CollectionUtils;
import org.qommons.config.DefaultQonfigParser;
import org.qommons.config.QommonsConfig;
import org.qommons.config.QonfigDocument;
import org.qommons.config.QonfigElement;
import org.qommons.config.QonfigElement.QonfigValue;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.config.QonfigParseException;
import org.qommons.config.QonfigToolkit;
import org.qommons.io.LocatedFilePosition;
import org.qommons.io.SimpleXMLParser;

/** A structure containing many {@link #getValues() style values} that may apply to all &lt;styled> elements in a document */
@ExElementTraceable(toolkit = QuickStyleInterpretation.STYLE, qonfigType = "style-sheet")
public class QuickStyleSheet extends ExElement.Def.Abstract<ExElement.Void> {
	@ExElementTraceable(toolkit = QuickStyleInterpretation.STYLE, qonfigType = "import-style-sheet")
	public static class StyleSheetRef extends ExElement.Def.Abstract<ExElement.Void> {
		private String theName;
		private QuickStyleSheet theTarget;
		private URL theReference;

		public StyleSheetRef(QuickStyleSheet parent, QonfigElementOrAddOn type) {
			super(parent, type);
		}

		@QonfigAttributeGetter("name")
		public String getName() {
			return theName;
		}

		@QonfigAttributeGetter("ref")
		public URL getReference() {
			return theReference;
		}

		public QuickStyleSheet getTarget() {
			return theTarget;
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			super.doUpdate(session);
			theName = session.getAttributeText("name");

			importStyleSheet(session);
		}

		private void importStyleSheet(ExpressoQIS session) throws QonfigInterpretationException {
			DefaultQonfigParser parser = new DefaultQonfigParser();
			for (QonfigToolkit tk : session.getElement().getDocument().getDocToolkit().getDependencies().values())
				parser.withToolkit(tk);
			QonfigValue address = session.attributes().get("ref").get();
			URL ref;
			try {
				String urlStr = QommonsConfig.resolve(address.text, session.getElement().getDocument().getLocation());
				ref = new URL(urlStr);
			} catch (IOException e) {
				throw new QonfigInterpretationException("Bad style-sheet reference: " + session.getAttributeText("ref"),
					address.position == null ? null : new LocatedFilePosition(address.fileLocation, address.position.getPosition(0)), //
						address.text.length(), e);
			}
			theReference = ref;
			QonfigDocument ssDoc;
			try (InputStream in = new BufferedInputStream(ref.openStream())) {
				ssDoc = parser.parseDocument(false, ref.toString(), in);
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
			ExpressoQIS importSession = session.interpretRoot(ssDoc.getRoot());
			QuickTypeStyle.TypeStyleSet styleTypeSet = session.get(QuickStyleElement.STYLE_TYPE_SET, QuickTypeStyle.TypeStyleSet.class);
			if (styleTypeSet == null) {
				styleTypeSet = new QuickTypeStyle.TypeStyleSet();
				session.putGlobal(QuickStyleElement.STYLE_TYPE_SET, styleTypeSet);
			}
			importSession.as(ExpressoQIS.class)//
			.setExpressoEnv(importSession.getExpressoEnv()
				.with(ObservableModelSet.build(address.text, session.getExpressoEnv().getModels().getNameChecker()).build()))//
			.put(QuickStyleElement.STYLE_TYPE_SET, styleTypeSet);
			if (theTarget == null)
				theTarget = importSession.interpret(QuickStyleSheet.class);
			theTarget.update(importSession);
		}
	}

	private final List<QuickStyleElement.Def> theStyleElements;
	private final List<StyleSheetRef> theStyleSheetRefs;
	private final Map<String, QuickStyleSheet> theImportedStyleSheets;
	private final List<QuickStyleSet> theStyleSetList;
	private final Map<String, QuickStyleSet> theStyleSets;

	public QuickStyleSheet(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
		super(parent, qonfigType);
		theStyleSetList = new ArrayList<>();
		theStyleSets = new HashMap<>();
		theStyleSheetRefs = new ArrayList<>();
		theImportedStyleSheets = new LinkedHashMap<>();
		theStyleElements = new ArrayList<>();
	}

	/** @return This style sheet's style sets, whose values can be inherited en-masse by name */
	public Map<String, QuickStyleSet> getStyleSets() {
		return Collections.unmodifiableMap(theStyleSets);
	}

	@QonfigChildGetter("style-set")
	public List<QuickStyleSet> getStyleSetList() {
		return theStyleSetList;
	}

	@QonfigChildGetter("style")
	public List<QuickStyleElement.Def> getStyleElements() {
		return Collections.unmodifiableList(theStyleElements);
	}

	/** @return All style sheets referred to by this style sheet */
	public Map<String, QuickStyleSheet> getImportedStyleSheets() {
		return Collections.unmodifiableMap(theImportedStyleSheets);
	}

	@QonfigChildGetter("style-sheet-ref")
	public List<StyleSheetRef> getImportedStyleSheetRefs() {
		return Collections.unmodifiableList(theStyleSheetRefs);
	}

	/**
	 * @param name The name of the style-set. May refer to a style set in an {@link #getImportedStyleSheets() imported} style sheet by using
	 *        the style sheet's name, dot (.), the style set name.
	 * @return All style values declared for the given style set
	 * @throws IllegalArgumentException If no such style-set was found
	 */
	public QuickStyleSet getStyleSet(String name) throws IllegalArgumentException {
		int dot = name.indexOf('.');
		if (dot >= 0) {
			QuickStyleSheet ss = theImportedStyleSheets.get(name.substring(0, dot));
			if (ss == null)
				throw new IllegalArgumentException("No such style-sheet found: '" + name.substring(0, dot) + "'");
			return ss.getStyleSet(name.substring(dot + 1));
		} else {
			QuickStyleSet styleSet = theStyleSets.get(name);
			if (styleSet == null)
				throw new IllegalArgumentException("No such style-set found: '" + name + "'");
			return styleSet;
		}
	}

	/**
	 * @param styleValues Collection into which to add style values in this style sheet that
	 *        {@link StyleApplicationDef#applies(QonfigElement) apply} to the given element
	 * @param element The element to get style values for
	 * @param env The compiled environment to validate against
	 * @throws QonfigInterpretationException If this style sheet's styles cannot be applied in the given environment
	 */
	public final void getStyleValues(Collection<QuickStyleValue> styleValues, QonfigElement element, CompiledExpressoEnv env)
		throws QonfigInterpretationException {
		ExWithRequiredModels.RequiredModelContext styleSheetModelContext = getAddOn(ExWithRequiredModels.Def.class).getContext(env);
		for (QuickStyleElement.Def style : theStyleElements)
			style.getStyleValues(styleValues, StyleApplicationDef.ALL, element, env, styleSheetModelContext);
		for (QuickStyleSheet imported : theImportedStyleSheets.values())
			imported.getStyleValues(styleValues, element, env);
	}

	@Override
	protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
		super.doUpdate(session);

		session.put(ExWithStyleSheet.QUICK_STYLE_SHEET, this);

		syncChildren(StyleSheetRef.class, theStyleSheetRefs, session.forChildren("style-sheet-ref"));
		theImportedStyleSheets.clear();
		for (StyleSheetRef ref : theStyleSheetRefs) {
			if (theImportedStyleSheets.put(ref.getName(), ref.getTarget()) != null)
				throw new QonfigInterpretationException("Multiple imported style sheets named '" + ref.getName() + "'",
					ref.reporting().getPosition(), 0);
		}

		// Parse style-sheets and style-sets first so they can be referred to
		syncChildren(QuickStyleSet.class, theStyleSetList, session.forChildren("style-set"));
		theStyleSets.clear();
		for (QuickStyleSet styleSet : theStyleSetList) {
			if (theStyleSets.put(styleSet.getName(), styleSet) != null)
				throw new QonfigInterpretationException("Multiple style sets named '" + styleSet.getName() + "'",
					styleSet.reporting().getPosition(), 0);
		}

		syncChildren(QuickStyleElement.Def.class, theStyleElements, session.forChildren("style"));
	}

	public Interpreted interpret(ExElement.Interpreted<?> parent) {
		return new Interpreted(this, parent);
	}

	/**
	 * Prints a representation of this style sheet to a StringBuilder
	 *
	 * @param str The string builder to append to. Null to create a new one.
	 * @param indent The amount by which to indent this style sheet in the string
	 * @return The string builder
	 */
	public StringBuilder print(StringBuilder str, int indent) {
		if (str == null)
			str = new StringBuilder();
		str.append("{");
		for (QuickStyleElement.Def style : theStyleElements) {
			indent(str, indent);
			str.append(style);
		}
		if (!theImportedStyleSheets.isEmpty() || !theStyleElements.isEmpty())
			str.append('\n');
		for (Map.Entry<String, QuickStyleSheet> imp : theImportedStyleSheets.entrySet()) {
			indent(str, indent);
			str.append(imp.getKey()).append("<-");
			imp.getValue().print(str, indent + 1).append('\n');
		}
		// TODO Style sets
		return str.append("}");
	}

	private void indent(StringBuilder str, int indent) {
		for (int i = 0; i < indent; i++)
			str.append('\t');
	}

	@Override
	public String toString() {
		return print(null, 0).toString();
	}

	public static class Interpreted extends ExElement.Interpreted.Abstract<ExElement.Void> {
		private final List<QuickStyleElement.Interpreted<?>> theStyleElements;
		private final Map<String, QuickStyleSheet.Interpreted> theImportedStyleSheets;
		private final Map<String, QuickStyleSet.Interpreted> theStyleSets;

		Interpreted(QuickStyleSheet definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
			theStyleElements = new ArrayList<>();
			theImportedStyleSheets = new LinkedHashMap<>();
			theStyleSets = new LinkedHashMap<>();
		}

		@Override
		public QuickStyleSheet getDefinition() {
			return (QuickStyleSheet) super.getDefinition();
		}

		public List<QuickStyleElement.Interpreted<?>> getStyleElements() {
			return Collections.unmodifiableList(theStyleElements);
		}

		public Map<String, QuickStyleSheet.Interpreted> getImportedStyleSheets() {
			return Collections.unmodifiableMap(theImportedStyleSheets);
		}

		public Map<String, QuickStyleSet.Interpreted> getStyleSets() {
			return Collections.unmodifiableMap(theStyleSets);
		}

		public Interpreted findInterpretation(QuickStyleSheet styleSheet) {
			if (styleSheet.getIdentity() == getIdentity())
				return this;
			for (Interpreted imported : theImportedStyleSheets.values()) {
				Interpreted found = imported.findInterpretation(styleSheet);
				if (found != null)
					return found;
			}
			return null;
		}

		public void updateStyleSheet(InterpretedExpressoEnv expressoEnv) throws ExpressoInterpretationException {
			update(expressoEnv);
		}

		@Override
		protected void doUpdate(InterpretedExpressoEnv expressoEnv) throws ExpressoInterpretationException {
			super.doUpdate(expressoEnv);

			syncChildren(getDefinition().getStyleElements(), theStyleElements, def -> def.interpret(this),
				(i, sEnv) -> i.updateStyle(sEnv));

			List<QuickStyleSheet.Interpreted> importedStyleSheets = new ArrayList<>(theImportedStyleSheets.values());
			theImportedStyleSheets.clear();
			CollectionUtils
			.synchronize(importedStyleSheets, new ArrayList<>(getDefinition().getImportedStyleSheets().entrySet()),
				(interp, def) -> interp.getIdentity() == def.getValue().getIdentity())//
			.<ExpressoInterpretationException> simpleE(def -> def.getValue().interpret(null))//
			.onLeftX(el -> el.getLeftValue().destroy())//
			.onRightX(el -> {
				el.getLeftValue().updateStyleSheet(expressoEnv);
				theImportedStyleSheets.put(el.getRightValue().getKey(), el.getLeftValue());
			})//
			.onCommonX(el -> {
				el.getLeftValue().updateStyleSheet(expressoEnv);
				theImportedStyleSheets.put(el.getRightValue().getKey(), el.getLeftValue());
			})//
			.rightOrder()//
			.adjust();

			List<QuickStyleSet.Interpreted> styleSets = new ArrayList<>(theStyleSets.values());
			theStyleSets.clear();
			CollectionUtils
			.synchronize(styleSets, new ArrayList<>(getDefinition().getStyleSets().entrySet()),
				(interp, def) -> interp.getIdentity() == def.getValue().getIdentity())//
			.<ExpressoInterpretationException> simpleE(def -> def.getValue().interpret(this))//
			.onLeftX(el -> el.getLeftValue().destroy())//
			.onRightX(el -> {
				el.getLeftValue().updateStyleSet(expressoEnv);
				theStyleSets.put(el.getRightValue().getKey(), el.getLeftValue());
			})//
			.onCommonX(el -> {
				el.getLeftValue().updateStyleSet(expressoEnv);
				theStyleSets.put(el.getRightValue().getKey(), el.getLeftValue());
			})//
			.rightOrder()//
			.adjust();
		}
	}
}
