package org.observe.quick.style;

import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.observe.expresso.ExpressoQIS;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExElement.Def;
import org.observe.expresso.qonfig.ExElement.Interpreted;
import org.qommons.config.AbstractQIS;
import org.qommons.config.QonfigElement;
import org.qommons.config.QonfigInterpretationException;

/** A structure containing many {@link #getValues() style values} that may apply to all &lt;styled> elements in a document */
public class QuickStyleSheet extends ExElement.Def.Abstract<ExElement> {
	public static class StyleSheetRef extends ExElement.Def.Abstract<ExElement> {
		private static final ExElement.AttributeValueGetter<ExElement, ExElement.Interpreted<?>, StyleSheetRef> NAME//
		= ExElement.AttributeValueGetter.of(StyleSheetRef::getName, null, null,
			"The name by which the imported style sheet may be referred to in the document");
		private static final ExElement.AttributeValueGetter<ExElement, ExElement.Interpreted<?>, StyleSheetRef> REF//
		= ExElement.AttributeValueGetter.<ExElement, ExElement.Interpreted<?>, StyleSheetRef> of(StyleSheetRef::getReference, null,
			null, "The URL location of the imported style sheet's data");

		private final String theName;
		private final QuickStyleSheet theTarget;

		StyleSheetRef(QuickStyleSheet parent, AbstractQIS<?> session, QuickStyleSheet target) {
			super(parent, session.getElement());
			theName = session.getAttributeText("name");
			theTarget = target;
			forAttribute(session.getAttributeDef(null, null, "name"), NAME);
			forAttribute(session.getAttributeDef(null, null, "ref"), REF);
		}

		public String getName() {
			return theName;
		}

		public QuickStyleSheet getTarget() {
			return theTarget;
		}

		public URL getReference() {
			return theTarget.getReference();
		}
	}

	private static final ExElement.ChildElementGetter<ExElement, ExElement.Interpreted<?>, QuickStyleSheet> IMPORTED//
	= new ExElement.ChildElementGetter<ExElement, ExElement.Interpreted<?>, QuickStyleSheet>() {
		@Override
		public String getDescription() {
			return "Style sheets imported by this style sheet";
		}

		@Override
		public List<? extends Def<?>> getChildrenFromDef(QuickStyleSheet def) {
			return def.getImportedStyleSheetRefs();
		}

		@Override
		public List<? extends Interpreted<?>> getChildrenFromInterpreted(Interpreted<?> interp) {
			return Collections.emptyList();
		}

		@Override
		public List<? extends ExElement> getChildrenFromElement(ExElement element) {
			return Collections.emptyList();
		}
	};
	private static final ExElement.ChildElementGetter<ExElement, ExElement.Interpreted<?>, QuickStyleSheet> STYLE_SETS//
	= new ExElement.ChildElementGetter<ExElement, ExElement.Interpreted<?>, QuickStyleSheet>() {
		@Override
		public String getDescription() {
			return "Style sets declared by this style sheet";
		}

		@Override
		public List<? extends Def<?>> getChildrenFromDef(QuickStyleSheet def) {
			return new ArrayList<>(def.getStyleSets().values());
		}

		@Override
		public List<? extends Interpreted<?>> getChildrenFromInterpreted(Interpreted<?> interp) {
			return Collections.emptyList();
		}

		@Override
		public List<? extends ExElement> getChildrenFromElement(ExElement element) {
			return Collections.emptyList();
		}
	};
	private static final ExElement.ChildElementGetter<ExElement, ExElement.Interpreted<?>, QuickStyleSheet> STYLE_ELEMENTS//
	= new ExElement.ChildElementGetter<ExElement, ExElement.Interpreted<?>, QuickStyleSheet>() {
		@Override
		public String getDescription() {
			return "Style sets declared by this style sheet";
		}

		@Override
		public List<? extends Def<?>> getChildrenFromDef(QuickStyleSheet def) {
			return def.getStyleElements();
		}

		@Override
		public List<? extends Interpreted<?>> getChildrenFromInterpreted(Interpreted<?> interp) {
			return Collections.emptyList();
		}

		@Override
		public List<? extends ExElement> getChildrenFromElement(ExElement element) {
			return Collections.emptyList();
		}
	};

	private final URL theReference;
	private final Map<String, QuickStyleSet> theStyleSets;
	private final List<QuickStyleValue<?>> theValues;
	private final Map<String, QuickStyleSheet> theImportedStyleSheets;
	private final List<StyleSheetRef> theStyleSheetRefs;
	private final List<QuickStyleElement.Def> theStyleElements;

	/**
	 * @param ref The location where this external style sheet was defined, or null if it is an inline style sheet
	 * @param styleSets All style values defined for specific style-sets
	 * @param values All values defined for the style sheet (not style-set specific)
	 * @param importedStyleSheets All style sheets imported into this one
	 */
	public QuickStyleSheet(ExElement.Def<?> parent, AbstractQIS<?> session, //
		URL ref, Map<String, QuickStyleSet> styleSets, List<QuickStyleValue<?>> values,
		Map<String, QuickStyleSheet> importedStyleSheets, List<StyleSheetRef> refs, List<QuickStyleElement.Def> styleElements) {
		super(parent, session.getElement());
		theReference = ref;
		theStyleSets = styleSets;
		theValues = values;
		theImportedStyleSheets = importedStyleSheets;
		theStyleSheetRefs = refs;
		theStyleElements = styleElements;
		forChild(session.getRole("style-sheet-ref"), IMPORTED);
		forChild(session.getRole("style-set"), STYLE_SETS);
		forChild(session.getRole("style"), STYLE_ELEMENTS);
	}

	/** @return The location where this standalone style sheet was parsed from */
	public URL getReference() {
		return theReference;
	}

	/** @return This style sheet's style sets, whose values can be inherited en-masse by name */
	public Map<String, QuickStyleSet> getStyleSets() {
		return theStyleSets;
	}

	/** @return The style values declared by this style sheet */
	public List<QuickStyleValue<?>> getValues() {
		return theValues;
	}

	public List<QuickStyleElement.Def> getStyleElements() {
		return theStyleElements;
	}

	/** @return All style sheets referred to by this style sheet */
	public Map<String, QuickStyleSheet> getImportedStyleSheets() {
		return theImportedStyleSheets;
	}

	public List<StyleSheetRef> getImportedStyleSheetRefs() {
		return theStyleSheetRefs;
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
	 * @param element The element to get style values for
	 * @return All style values in this style sheet that {@link StyleApplicationDef#applies(QonfigElement) apply} to the given element
	 */
	public final List<QuickStyleValue<?>> getValues(QonfigElement element) {
		List<QuickStyleValue<?>> values = new ArrayList<>();
		for (QuickStyleValue<?> ssv : theValues) {
			if (ssv.getApplication().applies(element))
				values.add(ssv);
			else if (ssv.isTrickleDown()) {
				QonfigElement parent = element.getParent();
				while (parent != null && !parent.isInstance(ssv.getAttribute().getDeclarer().getElement())) {
					if (ssv.getApplication().applies(parent)) {
						values.add(ssv);
						break;
					}
					parent = parent.getParent();
				}
			}
		}
		for (QuickStyleSheet imported : theImportedStyleSheets.values())
			values.addAll(imported.getValues(element));
		return values;
	}

	@Override
	public void update(ExpressoQIS session) throws QonfigInterpretationException {
		super.update(session);
		int i = 0;
		for (ExpressoQIS ssrSession : session.forChildren("style-sheet-ref"))
			theStyleSheetRefs.get(i++).update(ssrSession);
		for (ExpressoQIS ssSession : session.forChildren("style-set"))
			theStyleSets.get(ssSession.getAttributeText("name")).update(ssSession);
		i = 0;
		for (ExpressoQIS valueS : session.forChildren("style"))
			theStyleElements.get(i++).update(valueS);
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
		if (theReference != null)
			str.append(theReference);
		str.append("{");
		if (!theImportedStyleSheets.isEmpty() || !theValues.isEmpty())
			str.append('\n');
		for (Map.Entry<String, QuickStyleSheet> imp : theImportedStyleSheets.entrySet()) {
			indent(str, indent);
			str.append(imp.getKey()).append("<-");
			imp.getValue().print(str, indent + 1).append('\n');
		}
		//TODO Style sets
		for (QuickStyleValue<?> qsv : theValues) {
			indent(str, indent);
			str.append(qsv);
		}
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
}
