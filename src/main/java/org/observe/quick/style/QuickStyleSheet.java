package org.observe.quick.style;

import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.qommons.config.QonfigElement;

/** A structure containing many {@link #getValues() style values} that may apply to all &lt;styled> elements in a document */
public class QuickStyleSheet {
	/** A style sheet with no values */
	public static final QuickStyleSheet EMPTY = new QuickStyleSheet(null, Collections.emptyMap(), Collections.emptyList(),
		Collections.emptyMap());

	private final URL theReference;
	private final Map<String, List<QuickStyleValue<?>>> theStyleSets;
	private final List<QuickStyleValue<?>> theValues;
	private final Map<String, QuickStyleSheet> theImportedStyleSheets;

	/**
	 * @param ref The location where this external style sheet was defined, or null if it is an inline style sheet
	 * @param styleSets All style values defined for specific style-sets
	 * @param values All values defined for the style sheet (not style-set specific)
	 * @param importedStyleSheets All style sheets imported into this one
	 */
	public QuickStyleSheet(URL ref, Map<String, List<QuickStyleValue<?>>> styleSets, List<QuickStyleValue<?>> values,
		Map<String, QuickStyleSheet> importedStyleSheets) {
		theReference = ref;
		theStyleSets = styleSets;
		theValues = values;
		theImportedStyleSheets = importedStyleSheets;
	}

	/** @return The location where this standalone style sheet was parsed from */
	public URL getReference() {
		return theReference;
	}

	/** @return This style sheet's style sets, which only apply to elements that declare their style set */
	public Map<String, List<QuickStyleValue<?>>> getStyleSets() {
		return theStyleSets;
	}

	/** @return The style values declared by this style sheet */
	public List<QuickStyleValue<?>> getValues() {
		return theValues;
	}

	/** @return All style sheets referred to by this style sheet */
	public Map<String, QuickStyleSheet> getImportedStyleSheets() {
		return theImportedStyleSheets;
	}

	/**
	 * @param name The name of the style-set. May refer to a style set in an {@link #getImportedStyleSheets() imported} style sheet by using
	 *        the style sheet's name, dot (.), the style set name.
	 * @return All style values declared for the given style set
	 * @throws IllegalArgumentException If no such style-set was found
	 */
	public List<QuickStyleValue<?>> getStyleSet(String name) throws IllegalArgumentException {
		int dot = name.indexOf('.');
		if (dot >= 0) {
			QuickStyleSheet ss = theImportedStyleSheets.get(name.substring(0, dot));
			if (ss == null)
				throw new IllegalArgumentException("No such style-sheet found: '" + name.substring(0, dot) + "'");
			return ss.getStyleSet(name.substring(dot + 1));
		} else {
			List<QuickStyleValue<?>> values = theStyleSets.get(name);
			if (values == null)
				throw new IllegalArgumentException("No such style-set found: '" + name + "'");
			return values;
		}
	}

	/**
	 * @param element The element to get style values for
	 * @return All style values in this style sheet that {@link StyleValueApplication#applies(QonfigElement) apply} to the given element
	 */
	public final List<QuickStyleValue<?>> getValues(QonfigElement element) {
		List<QuickStyleValue<?>> values = new ArrayList<>();
		for (QuickStyleValue<?> ssv : theValues) {
			if (ssv.getApplication().applies(element))
				values.add(ssv);
		}
		for (QuickStyleSheet imported : theImportedStyleSheets.values()) {
			values.addAll(imported.getValues(element));
		}
		// TODO Style sets?
		return values;
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
