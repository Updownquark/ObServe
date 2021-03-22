package org.observe.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.observe.config.ObservableConfig.ObservableConfigEvent;
import org.qommons.Transaction;

/** Represents a pattern matching a particular descendant or set of descendants of an {@link ObservableConfig} */
public class ObservableConfigPath {
	/** Separates elements in a path string */
	public static final char PATH_SEPARATOR = '/';
	/** Separates elements in a path string */
	public static final String PATH_SEPARATOR_STR = "" + PATH_SEPARATOR;
	/** The empty string */
	public static final String EMPTY_PATH = "".intern();
	/** Matches any child (or all children) of a config element */
	public static final String ANY_NAME = "*".intern();
	/** Matches any descendant (or all descendants) of a config element */
	public static final String ANY_DEPTH = "**".intern();

	private final List<ObservableConfigPathElement> theElements;

	/** @param elements The elements of the path */
	protected ObservableConfigPath(List<ObservableConfigPathElement> elements) {
		this.theElements = elements;
	}

	/** @return The elements of the path */
	public List<ObservableConfigPathElement> getElements() {
		return theElements;
	}

	/** @return The last element of the path */
	public ObservableConfigPathElement getLastElement() {
		return theElements.get(theElements.size() - 1);
	}

	/** @return A config path containing only the last element of this path */
	public ObservableConfigPath getLast() {
		if (theElements.size() == 1)
			return this;
		return new ObservableConfigPath(theElements.subList(theElements.size() - 1, theElements.size()));
	}

	/** @return A config path containing all but the last element of this path */
	public ObservableConfigPath getParent() {
		if (theElements.size() == 1)
			return null;
		return new ObservableConfigPath(theElements.subList(0, theElements.size() - 1));
	}

	/**
	 * @param path A path of {@link ObservableConfig}s to match
	 * @return Whether the given config sequence matches this path
	 */
	public boolean matches(List<ObservableConfig> path) {
		Iterator<ObservableConfigPathElement> matcherIter = theElements.iterator();
		Iterator<ObservableConfig> pathIter = path.iterator();
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
		return obj instanceof ObservableConfigPath && theElements.equals(((ObservableConfigPath) obj).theElements);
	}

	@Override
	public String toString() {
		if (theElements.size() == 1)
			return theElements.get(0).toString();
		StringBuilder str = new StringBuilder();
		boolean first = true;
		for (ObservableConfigPathElement el : theElements) {
			if (first)
				first = false;
			else
				str.append(ObservableConfigPath.PATH_SEPARATOR);
			str.append(el.toString());
		}
		return str.toString();
	}

	/**
	 * @param firstName The name of the first element in the path
	 * @return A builder for the path
	 */
	public static ObservableConfigPathBuilder buildPath(String firstName) {
		return buildPath(new LinkedList<>(), firstName);
	}

	/**
	 * @param path Previous elements in the path
	 * @param name The name of the next element in the path
	 * @return The builder for the next element of the path
	 */
	protected static ObservableConfigPathBuilder buildPath(List<ObservableConfigPathElement> path, String name) {
		List<ObservableConfigPathElement> finalPath = new ArrayList<>(path.size());
		finalPath.addAll(path);
		Map<String, String> properties = null;
		// Quick check to avoid pattern checking on every single path, few of which will have attributes
		if (name.length() > 0 && name.charAt(name.length() - 1) == '}') {
			properties = new LinkedHashMap<>();
			name = parsePathProperties(name, properties);
		}
		ObservableConfigPathBuilder builder = new ObservableConfigPathBuilder(finalPath, name);
		if (properties != null) {
			for (Map.Entry<String, String> property : properties.entrySet()) {
				builder.withAttribute(property.getKey(), property.getValue());
			}
		}
		return builder;
	}

	/**
	 * @param path The elements in the path
	 * @return The path
	 */
	protected static ObservableConfigPath createPath(List<ObservableConfigPathElement> path) {
		return new ObservableConfigPath(Collections.unmodifiableList(path));
	}

	/**
	 * @param path The path string to parse
	 * @return The path represented by the given string
	 */
	public static ObservableConfigPath create(String path) {
		if (path.length() == 0)
			return null;
		String[] split = parsePath(path);
		ObservableConfigPathBuilder builder = null;
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

	/** An element in a {@link ObservableConfigPath} */
	public static class ObservableConfigPathElement {
		private final String theName;
		private final Map<String, String> theAttributes;
		private final boolean isMulti;
		private final boolean isMultiDepth;

		/**
		 * @param name The name for the element
		 * @param attributes The attribute/value pairs for the element
		 * @param multi Whether this element is intended to match multiple siblings
		 * @param multiDepth Whether this element is intended to match any descendant of the parent
		 */
		protected ObservableConfigPathElement(String name, Map<String, String> attributes, boolean multi, boolean multiDepth) {
			theName = name;
			theAttributes = attributes;
			isMulti = multi || multiDepth;
			isMultiDepth = multiDepth;
		}

		/** @return The name for the element */
		public String getName() {
			return theName;
		}

		/** @return The attribute/value pairs for the element */
		public Map<String, String> getAttributes() {
			return theAttributes;
		}

		/** @return Whether this element is intended to match multiple siblings */
		public boolean isMulti() {
			return isMulti;
		}

		/** @return Whether this element is intended to match any descendant of the parent */
		public boolean isMultiDepth() {
			return isMultiDepth;
		}

		/**
		 * @param config The config to test
		 * @return Whether the given config element may be a match for this path element
		 */
		public boolean matches(ObservableConfig config) {
			if (!isMulti && !theName.equals(config.getName()))
				return false;
			if (!theAttributes.isEmpty()) {
				try (Transaction t = config.lock(false, null)) {
					for (Map.Entry<String, String> attr : theAttributes.entrySet()) {
						boolean found = false;
						for (ObservableConfig child : config.getContent()) {
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

		/**
		 * @param config The config to test
		 * @param change The config change representing a change event that just occurred
		 * @return Whether the given config element may have been a match for this element before the given event
		 */
		public boolean matchedBefore(ObservableConfig config, ObservableConfigEvent change) {
			if (!theName.equals(ObservableConfigPath.ANY_NAME)) {
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
							for (ObservableConfig child : config.getContent()) {
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
			return Objects.hash(theName, theAttributes, isMulti, isMultiDepth);
		}

		@Override
		public boolean equals(Object obj) {
			if (obj == this)
				return true;
			else if (!(obj instanceof ObservableConfigPathElement))
				return false;
			ObservableConfigPathElement other = (ObservableConfigPathElement) obj;
			return theName.equals(other.theName) && Objects.equals(theAttributes, other.theAttributes) && isMulti == other.isMulti
				&& isMultiDepth == other.isMultiDepth;
		}

		@Override
		public String toString() {
			if (theAttributes.isEmpty() && !isMulti)
				return theName;
			StringBuilder str = new StringBuilder(theName);
			if (isMultiDepth)
				str.append(ObservableConfigPath.ANY_DEPTH);
			else if (isMulti)
				str.append(ObservableConfigPath.ANY_NAME);
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

	/** Builds an {@link ObservableConfigPath} */
	public static class ObservableConfigPathBuilder {
		private final List<ObservableConfigPathElement> thePath;
		private final String theName;
		private Map<String, String> theAttributes;
		private boolean isMulti;
		private boolean isMultiDepth;
		private boolean isUsed;

		/**
		 * @param path Previous path elements
		 * @param name The name of the config element
		 */
		protected ObservableConfigPathBuilder(List<ObservableConfigPathElement> path, String name) {
			thePath = path;
			theName = name;
		}

		/**
		 * Causes the path element to only match config elements with a given child
		 *
		 * @param attrName The name of the attribute/child
		 * @param value The value for the child
		 * @return This builder
		 */
		public ObservableConfigPathBuilder withAttribute(String attrName, String value) {
			if (isUsed)
				throw new IllegalStateException("This builder has already been used");
			if (theAttributes == null)
				theAttributes = new LinkedHashMap<>();
			theAttributes.put(attrName, value);
			return this;
		}

		/**
		 * Causes the path element to be a matcher for multiple elements
		 *
		 * @param deep Whether to match any descendant as opposed to just any sibling of the parent
		 * @return This builder
		 */
		public ObservableConfigPathBuilder multi(boolean deep) {
			if (isUsed)
				throw new IllegalStateException("This builder has already been used");
			isMulti = true;
			isMultiDepth = deep;
			return this;
		}

		/** Prevents further configuration */
		protected void seal() {
			if (isUsed)
				throw new IllegalStateException("This builder has already been used");
			isUsed = true;
			thePath.add(new ObservableConfigPathElement(theName,
				theAttributes == null ? Collections.emptyMap() : Collections.unmodifiableMap(theAttributes), isMulti, isMultiDepth));
		}

		/**
		 * @param name The name for the child
		 * @return A builder for a child path element
		 */
		public ObservableConfigPathBuilder andThen(String name) {
			seal();
			return buildPath(thePath, name);
		}

		/** @return The config path */
		public ObservableConfigPath build() {
			seal();
			return createPath(thePath);
		}
	}

	static String[] parsePath(String path) {
		int pathSepIdx = path.indexOf(ObservableConfigPath.PATH_SEPARATOR);
		if (pathSepIdx < 0)
			return new String[] { path };
		List<String> pathEls = new LinkedList<>();
		int lastSep = -1;
		while (pathSepIdx >= 0) {
			pathEls.add(path.substring(lastSep + 1, pathSepIdx));
			lastSep = pathSepIdx;
			pathSepIdx = path.indexOf(ObservableConfigPath.PATH_SEPARATOR, pathSepIdx + 1);
		}
		pathEls.add(path.substring(lastSep + 1));
		return pathEls.toArray(new String[pathEls.size()]);
	}

	/**
	 * Parses an element from a path string
	 *
	 * @param pathEl The string representing an {@link ObservableConfigPathElement}
	 * @return The path element
	 */
	public static ObservableConfigPathElement parsePathElement(String pathEl) {
		boolean multi;
		boolean deep = ObservableConfigPath.ANY_DEPTH.equals(pathEl);
		String name;
		if (deep) {
			name = "";
			multi = true;
		} else {
			multi = pathEl.length() > 0 && pathEl.charAt(pathEl.length() - 1) == ObservableConfigPath.ANY_NAME.charAt(0);
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
		return new ObservableConfigPathElement(name, properties, multi, deep);
	}

	/**
	 * @param pathEl The path element to parse properties from
	 * @param properties The properties map to populate
	 * @return The name of the config element configured for the path element
	 */
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