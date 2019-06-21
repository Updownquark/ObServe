package org.observe.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.observe.SettableValue;
import org.observe.Subscription;
import org.observe.collect.CollectionChangeType;
import org.observe.collect.ObservableCollection;
import org.qommons.Causable;
import org.qommons.StructuredTransactable;
import org.qommons.Transaction;
import org.qommons.collect.BetterList;
import org.qommons.collect.CollectionLockingStrategy;
import org.qommons.collect.ElementId;
import org.qommons.collect.ListenerList;
import org.qommons.io.Format;
import org.qommons.tree.BetterTreeList;

public class ObservableConfig implements StructuredTransactable {
	public static final char PATH_SEPARATOR = '/';
	public static final String PATH_SEPARATOR_STR = "" + PATH_SEPARATOR;
	public static final String EMPTY_PATH = "".intern();
	public static final String ANY_NAME = "*".intern();
	public static final String MULTI_PATH = "**".intern();

	public class ObservableConfigPath {
		private final List<ObservableConfigPathElement> theElements;

		protected ObservableConfigPath(List<ObservableConfigPathElement> elements) {
			this.theElements = elements;
		}

		public List<ObservableConfigPathElement> getElements() {
			return theElements;
		}

		public ObservableConfigPathElement getLastElement() {
			return theElements.get(theElements.size() - 1);
		}

		public boolean matches(List<ObservableConfig> path) {
			Iterator<ObservableConfigPathElement> matcherIter = theElements.iterator();
			Iterator<ObservableConfig> pathIter = path.iterator();
			while (matcherIter.hasNext() && pathIter.hasNext())
				if (!matcherIter.next().matches(pathIter.next()))
					return false;
			if (matcherIter.hasNext())
				return false;
			else if (pathIter.hasNext() && !getLastElement().getName().equals(MULTI_PATH)) {
				return false;
			} else
				return true;
		}

		// TODO hashCode,equals

		@Override
		public String toString() {
			return theElements.toString();
		}
	}

	public class ObservableConfigPathElement {
		private final String theName;
		private final Map<String, String> theAttributes;

		protected ObservableConfigPathElement(String name, Map<String, String> attributes) {
			theName = name;
			theAttributes = attributes;
		}

		public String getName() {
			return theName;
		}

		public Map<String, String> getAttributes() {
			return theAttributes;
		}

		public boolean matches(ObservableConfig config) {
			if (!theName.equals(ANY_NAME) && !theName.equals(config.getName()))
				return false;
			try (Transaction t = config.lock(false, null)) {
				for (Map.Entry<String, String> attr : theAttributes.entrySet()) {
					boolean found = false;
					for (ObservableConfig child : config.theContent) {
						if (!child.getName().equals(attr.getKey()))
							continue;
						if (attr.getValue() != null && !attr.getValue().equals(child.getValue()))
							continue;
					}
					if (!found)
						return false;
				}
			}
			return true;
		}

		// TODO hashCode, equals, toString
	}

	public class ObservableConfigPathBuilder {
		private final ObservableConfig theTarget;
		private final List<ObservableConfigPathElement> thePath;
		private final String theName;
		private Map<String, String> theAttributes;
		private boolean isUsed;

		protected ObservableConfigPathBuilder(ObservableConfig target, List<ObservableConfigPathElement> path, String name) {
			theTarget = target;
			thePath = path;
			theName = name;
		}

		public ObservableConfigPathBuilder withAttribute(String attrName, String value) {
			if (theAttributes == null)
				theAttributes = new LinkedHashMap<>();
			theAttributes.put(attrName, value);
			return this;
		}

		protected void seal() {
			if (isUsed)
				throw new IllegalStateException("This class cannot be used twice");
			isUsed = true;
			thePath.add(new ObservableConfigPathElement(theName,
				theAttributes == null ? Collections.emptyMap() : Collections.unmodifiableMap(theAttributes)));
		}

		public ObservableConfigPathBuilder andThen(String name) {
			if (MULTI_PATH.equals(theName))
				throw new IllegalArgumentException(MULTI_PATH + " can only be used for the last element in a path");
			seal();
			return theTarget.buildPath(thePath, name);
		}

		public ObservableConfigPath build() {
			seal();
			return theTarget.createPath(thePath);
		}
	}

	public class ObservableConfigEvent extends Causable {
		public final CollectionChangeType changeType;
		public final List<ObservableConfig> relativePath;

		public ObservableConfigEvent(CollectionChangeType changeType, List<ObservableConfig> relativePath, Object cause) {
			super(cause);
			this.changeType = changeType;
			this.relativePath = relativePath;
		}

		public String getRelativePathString() {
			StringBuilder str = new StringBuilder();
			for (ObservableConfig p : relativePath) {
				if (str.length() > 0)
					str.append(PATH_SEPARATOR);
				str.append(p.getName());
			}
			return str.toString();
		}
	}

	private final ObservableConfig theParent;
	private final ElementId theParentContentRef;
	private final String theName;
	private String theValue;
	private final BetterList<ObservableConfig> theContent;
	private final ListenerList<InternalObservableConfigListener> theListeners;

	protected ObservableConfig(ObservableConfig parent, ElementId parentContentRef, String name, CollectionLockingStrategy locking,
		String value) {
		theParent = parent;
		theParentContentRef = parentContentRef;
		theName = name;
		theContent = new BetterTreeList<>(locking);
		theListeners = ListenerList.build().build();
		theValue = value;
	}

	public String getName() {
		return theName;
	}

	public String getValue() {
		return theValue;
	}

	public ObservableConfig getParent() {
		return theParent;
	}

	public String getPath() {
		if (theParent != null)
			return theParent.getPath() + PATH_SEPARATOR_STR + theName;
		else
			return theName;
	}

	public int getIndexInParent() {
		if (theParentContentRef == null || !theParentContentRef.isPresent())
			return -1;
		return theParent.theContent.getElementsBefore(theParentContentRef);
	}

	public ObservableConfigPathBuilder buildPath(String firstName) {
		return buildPath(new LinkedList<>(), firstName);
	}

	protected ObservableConfigPathBuilder buildPath(List<ObservableConfigPathElement> path, String name) {
		List<ObservableConfigPathElement> finalPath = new ArrayList<>(path.size());
		finalPath.addAll(path);
		return new ObservableConfigPathBuilder(this, Collections.unmodifiableList(finalPath), name);
	}

	protected ObservableConfigPath createPath(List<ObservableConfigPathElement> path) {
		return new ObservableConfigPath(Collections.unmodifiableList(path));
	}

	public ObservableConfigPath createPath(String path) {
		String[] split = parsePath(path);
		ObservableConfigPathBuilder builder = buildPath(split[0]);
		for (int i = 1; i < split.length; i++)
			builder = builder.andThen(split[i]);
		return builder.build();
	}

	public ObservableConfig createChild(ElementId parentContentRef, String name, CollectionLockingStrategy locking, String value) {
		return new ObservableConfig(this, parentContentRef, name, locking, value);
	}

	public Subscription watch(String pathFilter, Consumer<ObservableConfigEvent> listener) {
		return watch(createPath(pathFilter), listener);
	}

	public Subscription watch(ObservableConfigPath path, Consumer<ObservableConfigEvent> listener) {
		return theListeners.add(new InternalObservableConfigListener(path, listener), true)::run;
	}

	public ObservableCollection<ObservableConfig> getAllContent() {}

	public ObservableCollection<ObservableConfig> getContent(String path) {
		return getContentLike(path);
	}

	public ObservableCollection<ObservableConfig> getContentLike(String path, String... attributes) {}

	public SettableValue<String> observeValue() {}

	public <T> SettableValue<T> observeValue(Format<T> format) {}

	public SettableValue<String> observeValue(String path) {}

	public <T> SettableValue<T> observeValue(String path, Format<T> format) {}

	@Override
	public boolean isLockSupported() {
		return theContent.isLockSupported();
	}

	@Override
	public Transaction lock(boolean write, boolean structural, Object cause) {
		// TODO Do something with the cause if write
		return theContent.lock(write, structural, cause);
	}

	@Override
	public Transaction tryLock(boolean write, boolean structural, Object cause) {
		// TODO Do something with the cause if write
		return theContent.tryLock(write, structural, cause);
	}

	public ObservableConfig addChild(String name) {}

	public ObservableConfig setValue(String value) {}

	public ObservableConfig set(String path, String value) {}

	protected final Object getCurrentCause() {}

	protected void fire(CollectionChangeType eventType, List<ObservableConfig> relativePath) {
		ObservableConfigEvent event = new ObservableConfigEvent(eventType, relativePath, getCurrentCause());
		try (Transaction t = Causable.use(event)) {
			theListeners.forEach(intL -> {
				if (intL.path == null || intL.path.matches(relativePath))
					intL.listener.accept(event);
			});
		}
	}

	private static class InternalObservableConfigListener {
		final ObservableConfigPath path;
		final Consumer<ObservableConfigEvent> listener;

		InternalObservableConfigListener(ObservableConfigPath path, Consumer<ObservableConfigEvent> listener) {
			this.path = path;
			this.listener = listener;
		}
	}

	static String[] parsePath(String path) {
		int pathSepIdx = path.indexOf(PATH_SEPARATOR);
		if (pathSepIdx < 0)
			return new String[] { path };
		List<String> pathEls = new LinkedList<>();
		while (pathSepIdx >= 0) {
			pathEls.add(path.substring(0, pathSepIdx));
		}
		return pathEls.toArray(new String[pathEls.size()]);
	}
}
