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
			if (ssv.applies(element))
				values.add(ssv);
		}
		return values;
	}
}
