package org.observe.quick.style;

import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.qommons.config.QonfigElement;

public class QuickStyleSheet {
	public static final QuickStyleSheet EMPTY = new QuickStyleSheet(null, Collections.emptyMap(), Collections.emptyList(),
		Collections.emptyMap());

	private final URL theReference;
	private final Map<String, List<QuickStyleValue<?>>> theStyleSets;
	private final List<QuickStyleValue<?>> theValues;
	private final Map<String, QuickStyleSheet> theImportedStyleSheets;

	public QuickStyleSheet(URL ref, Map<String, List<QuickStyleValue<?>>> styleSets, List<QuickStyleValue<?>> values,
		Map<String, QuickStyleSheet> importedStyleSheets) {
		theReference = ref;
		theStyleSets = styleSets;
		theValues = values;
		theImportedStyleSheets = importedStyleSheets;
	}

	public URL getReference() {
		return theReference;
	}

	public Map<String, List<QuickStyleValue<?>>> getStyleSets() {
		return theStyleSets;
	}

	public List<QuickStyleValue<?>> getValues() {
		return theValues;
	}

	public Map<String, QuickStyleSheet> getImportedStyleSheets() {
		return theImportedStyleSheets;
	}

	public List<QuickStyleValue<?>> getStyleSet(String name) {
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
