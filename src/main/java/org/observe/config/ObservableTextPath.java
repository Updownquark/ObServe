package org.observe.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.qommons.Transaction;

public class ObservableTextPath {
	public static final String ANY_DEPTH = "**".intern();
	public static final String ANY_NAME = "*".intern();
	public static final String EMPTY_PATH = "".intern();
	public static final char PATH_SEPARATOR = '/';
	public static final String PATH_SEPARATOR_STR = "" + PATH_SEPARATOR;

	private final List<ObservableTextPathElement> theElements;

	protected ObservableTextPath(List<ObservableTextPathElement> elements) {
		this.theElements = elements;
	}

	public List<ObservableTextPathElement> getElements() {
		return theElements;
	}

	public ObservableTextPathElement getLastElement() {
		return theElements.get(theElements.size() - 1);
	}

	public ObservableTextPath getLast() {
		if (theElements.size() == 1)
			return this;
		return new ObservableTextPath(theElements.subList(theElements.size() - 1, theElements.size()));
	}

	public ObservableTextPath getParent() {
		if (theElements.size() == 1)
			return null;
		return new ObservableTextPath(theElements.subList(0, theElements.size() - 1));
	}

	public boolean matches(List<ObservableTextStructure> path) {
		Iterator<ObservableTextPathElement> matcherIter = theElements.iterator();
		Iterator<ObservableTextStructure> pathIter = path.iterator();
		while (matcherIter.hasNext()) {
			if (pathIter.hasNext()) {
				if (!matcherIter.next().matches(pathIter.next()))
					return false;
			} else if (!matcherIter.next().isMultiDepth())
				return false;
		}
		if (matcherIter.hasNext())
			return false;
		else if (pathIter.hasNext() && !getLastElement().isMultiDepth())
			return false;
		else
			return true;
	}

	@Override
	public int hashCode() {
		return theElements.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == this)
			return true;
		return obj instanceof ObservableTextPath && theElements.equals(((ObservableTextPath) obj).theElements);
	}

	@Override
	public String toString() {
		if (theElements.size() == 1)
			return theElements.get(0).toString();
		StringBuilder str = new StringBuilder();
		boolean first = true;
		for (ObservableTextPathElement el : theElements) {
			if (first)
				first = false;
			else
				str.append(ObservableTextPath.PATH_SEPARATOR);
			str.append(el.toString());
		}
		return str.toString();
	}

	public static class ObservableTextPathElement {
		private final String theName;
		private final Map<String, String> theAttributes;
		private final boolean isMulti;
		private final boolean isMultiDepth;

		protected ObservableTextPathElement(String name, Map<String, String> attributes, boolean multi, boolean multiDepth) {
			theName = name;
			theAttributes = attributes;
			isMulti = multi || multiDepth;
			isMultiDepth = multiDepth;
		}

		public String getName() {
			return theName;
		}

		public Map<String, String> getAttributes() {
			return theAttributes;
		}

		public boolean isMulti() {
			return isMulti;
		}

		public boolean isMultiDepth() {
			return isMultiDepth;
		}

		public boolean matches(ObservableTextStructure config) {
			if (!isMulti && !theName.equals(config.getName()))
				return false;
			if (!theAttributes.isEmpty()) {
				try (Transaction t = config.lock(false, null)) {
					for (Map.Entry<String, String> attr : theAttributes.entrySet()) {
						boolean found = false;
						for (ObservableTextStructure child : config.getContent()) {
							if (!child.getName().equals(attr.getKey()))
								continue;
							if (attr.getValue() == null || attr.getValue().equals(child.getValue())) {
								found = true;
								break;
							}
						}
						if (!found)
							return false;
					}
				}
			}
			return true;
		}

		public boolean matchedBefore(ObservableTextStructure config, ObservableTextStructure.ObservableStructureEvent change) {
			if (!theName.equals(ObservableTextPath.ANY_NAME)) {
				if (change.relativePath.size() == 1) {
					if (!theName.equals(change.oldName))
						return false;
				} else if (!theName.equals(config.getName()))
					return false;
			}
			if (!theAttributes.isEmpty()) {
				try (Transaction t = config.lock(false, null)) {
					for (Map.Entry<String, String> attr : theAttributes.entrySet()) {
						boolean found = false;

						if (change.relativePath.size() == 1 && change.oldName.equals(attr.getKey()))
							found = attr.getValue() == null || attr.getValue().equals(change.oldValue);
						if (!found) {
							for (ObservableTextStructure child : config.getContent()) {
								if (!child.getName().equals(attr.getKey()))
									continue;
								if (attr.getValue() != null && !attr.getValue().equals(child.getValue()))
									continue;
							}
						}
						if (!found)
							return false;
					}
				}
			}
			return true;
		}

		@Override
		public int hashCode() {
			return Objects.hash(theName, theAttributes, isMulti);
		}

		@Override
		public boolean equals(Object obj) {
			if (obj == this)
				return true;
			else if (!(obj instanceof ObservableTextPathElement))
				return false;
			ObservableTextPathElement other = (ObservableTextPathElement) obj;
			return theName.equals(other.theName) && Objects.equals(theAttributes, other.theAttributes) && isMulti == other.isMulti;
		}

		@Override
		public String toString() {
			if (theAttributes.isEmpty() && !isMulti)
				return theName;
			StringBuilder str = new StringBuilder(theName);
			if (isMultiDepth)
				str.append(ObservableTextPath.ANY_DEPTH);
			else if (isMulti)
				str.append(ObservableTextPath.ANY_NAME);
			if (!theAttributes.isEmpty()) {
				str.append('{');
				boolean first = true;
				for (Map.Entry<String, String> attr : theAttributes.entrySet()) {
					if (first)
						first = false;
					else
						str.append(',');
					str.append(attr.getKey()).append('=').append(attr.getValue());
				}
				str.append('}');
			}
			return str.toString();
		}
	}

	public static class ObservableTextPathBuilder {
		private final List<ObservableTextPathElement> thePath;
		private final String theName;
		private Map<String, String> theAttributes;
		private boolean isMulti;
		private boolean isMultiDepth;
		private boolean isUsed;

		protected ObservableTextPathBuilder(List<ObservableTextPathElement> path, String name) {
			thePath = path;
			theName = name;
		}

		public ObservableTextPathBuilder withAttribute(String attrName, String value) {
			if (isUsed)
				throw new IllegalStateException("This builder has already been used");
			if (theAttributes == null)
				theAttributes = new LinkedHashMap<>();
			theAttributes.put(attrName, value);
			return this;
		}

		public ObservableTextPathBuilder multi(boolean deep) {
			if (isUsed)
				throw new IllegalStateException("This builder has already been used");
			isMulti = true;
			isMultiDepth = deep;
			return this;
		}

		protected void seal() {
			if (isUsed)
				throw new IllegalStateException("This builder has already been used");
			isUsed = true;
			thePath.add(new ObservableTextPathElement(theName,
				theAttributes == null ? Collections.emptyMap() : Collections.unmodifiableMap(theAttributes), isMulti, isMultiDepth));
		}

		public ObservableTextPathBuilder andThen(String name) {
			seal();
			return buildPath(thePath, name);
		}

		public ObservableTextPath build() {
			seal();
			return createPath(thePath);
		}
	}

	public static ObservableTextPathBuilder buildPath(String firstName) {
		return buildPath(new LinkedList<>(), firstName);
	}

	private static ObservableTextPathBuilder buildPath(List<ObservableTextPathElement> path, String name) {
		List<ObservableTextPathElement> finalPath = new ArrayList<>(path.size());
		finalPath.addAll(path);
		Map<String, String> properties = null;
		// Quick check to avoid pattern checking on every single path, few of which will have attributes
		if (name.length() > 0 && name.charAt(name.length() - 1) == '}') {
			properties = new LinkedHashMap<>();
			name = parsePathProperties(name, properties);
		}
		ObservableTextPathBuilder builder = new ObservableTextPathBuilder(finalPath, name);
		if (properties != null) {
			for (Map.Entry<String, String> property : properties.entrySet()) {
				builder.withAttribute(property.getKey(), property.getValue());
			}
		}
		return builder;
	}

	private static ObservableTextPath createPath(List<ObservableTextPathElement> path) {
		return new ObservableTextPath(Collections.unmodifiableList(path));
	}

	public static ObservableTextPath createPath(String path) {
		if (path.length() == 0)
			return null;
		String[] split = parsePath(path);
		ObservableTextPathBuilder builder = null;
		for (int i = 0; i < split.length; i++) {
			boolean multi;
			boolean deep = ANY_DEPTH.equals(split[i]);
			String name;
			if (deep) {
				name = "";
				multi = true;
			} else {
				multi = split[i].length() > 0 && split[i].charAt(split[i].length() - 1) == ANY_NAME.charAt(0);
				if (multi)
					name = split[i].substring(0, split[i].length() - 1);
				else
					name = split[i];
			}
			if (i == 0)
				builder = buildPath(name);
			else
				builder = builder.andThen(name);
			if (multi)
				builder.multi(deep);
		}
		return builder.build();
	}

	private static String[] parsePath(String path) {
		int pathSepIdx = path.indexOf(PATH_SEPARATOR);
		if (pathSepIdx < 0)
			return new String[] { path };
		List<String> pathEls = new LinkedList<>();
		int lastSep = -1;
		while (pathSepIdx >= 0) {
			pathEls.add(path.substring(lastSep + 1, pathSepIdx));
			lastSep = pathSepIdx;
			pathSepIdx = path.indexOf(PATH_SEPARATOR, pathSepIdx + 1);
		}
		pathEls.add(path.substring(lastSep + 1));
		return pathEls.toArray(new String[pathEls.size()]);
	}

	public static ObservableTextPathElement parsePathElement(String pathEl) {
		boolean multi;
		boolean deep = ANY_DEPTH.equals(pathEl);
		String name;
		if (deep) {
			name = "";
			multi = true;
		} else {
			multi = pathEl.length() > 0 && pathEl.charAt(pathEl.length() - 1) == ANY_NAME.charAt(0);
			if (multi)
				name = pathEl.substring(0, pathEl.length() - 1);
			else
				name = pathEl;
		}
		Map<String, String> properties = null;
		// Quick check to avoid pattern checking on every single path, few of which will have attributes
		if (name.length() > 0 && name.charAt(name.length() - 1) == '}') {
			properties = new LinkedHashMap<>();
			name = parsePathProperties(name, properties);
		}
		return new ObservableTextPathElement(name, properties, multi, deep);
	}

	public static String parsePathProperties(String pathEl, Map<String, String> properties) {
		if (pathEl.length() == 0 || pathEl.charAt(pathEl.length() - 1) != '}')
			return pathEl;
		int index = pathEl.indexOf('{');
		int nameEnd = index;
		if (index < 0)
			return pathEl;
		int nextIdx = pathEl.indexOf(',', index + 1);
		if (nextIdx < 0)
			nextIdx = pathEl.length() - 1; // '}'
		while (nextIdx >= 0) {
			int eqIdx = pathEl.indexOf('=', index + 1);
			if (eqIdx < 0 || eqIdx > nextIdx)
				return pathEl;
			properties.put(pathEl.substring(index + 1, eqIdx).trim(), pathEl.substring(eqIdx + 1, nextIdx).trim());
			if (nextIdx == pathEl.length() - 1)
				break;
			index = nextIdx;
			nextIdx = pathEl.indexOf(',', index + 1);
			if (nextIdx < 0)
				nextIdx = pathEl.length() - 1; // '}'
		}
		String name = pathEl.substring(0, nameEnd).trim();
		return name;
	}
}