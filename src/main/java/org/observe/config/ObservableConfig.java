package org.observe.config;

import java.util.ArrayList;
import java.util.Arrays;
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
import org.observe.collect.Equivalence;
import org.observe.collect.ObservableCollection;
import org.observe.util.TypeTokens;
import org.qommons.Causable;
import org.qommons.StructuredTransactable;
import org.qommons.Transaction;
import org.qommons.collect.BetterList;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.CollectionLockingStrategy;
import org.qommons.collect.ElementId;
import org.qommons.collect.ListenerList;
import org.qommons.collect.MutableCollectionElement;
import org.qommons.io.Format;
import org.qommons.tree.BetterTreeList;

import com.google.common.reflect.TypeToken;

public class ObservableConfig implements StructuredTransactable {
	public static final char PATH_SEPARATOR = '/';
	public static final String PATH_SEPARATOR_STR = "" + PATH_SEPARATOR;
	public static final String EMPTY_PATH = "".intern();
	public static final String ANY_NAME = "*".intern();
	public static final String MULTI_PATH = "**".intern();
	public static final TypeToken<ObservableConfig> TYPE = TypeTokens.get().of(ObservableConfig.class);

	public static class ObservableConfigPath {
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

		public ObservableConfigPath getParent() {
			if (theElements.size() == 1)
				return null;
			return new ObservableConfigPath(theElements.subList(0, theElements.size() - 1));
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

	public static class ObservableConfigPathElement {
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

	public static class ObservableConfigPathBuilder {
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

	public static class ObservableConfigEvent extends Causable {
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
	private final CollectionLockingStrategy theLocking;
	private final String theName;
	private String theValue;
	private final BetterList<ObservableConfig> theContent;
	private final ListenerList<InternalObservableConfigListener> theListeners;
	private long theStructureModCount;
	private long theModCount;

	protected ObservableConfig(ObservableConfig parent, ElementId parentContentRef, String name, CollectionLockingStrategy locking,
		String value) {
		theParent = parent;
		theParentContentRef = parentContentRef;
		theLocking = locking;
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
		if (path.length() == 0)
			return null;
		String[] split = parsePath(path);
		ObservableConfigPathBuilder builder = buildPath(split[0]);
		for (int i = 1; i < split.length; i++)
			builder = builder.andThen(split[i]);
		return builder.build();
	}

	protected TypeToken<? extends ObservableConfig> getType() {
		return TYPE;
	}

	protected ObservableConfig createChild(ElementId parentContentRef, String name, CollectionLockingStrategy locking, String value) {
		return new ObservableConfig(this, parentContentRef, name, locking, value);
	}

	public Subscription watch(String pathFilter, Consumer<ObservableConfigEvent> listener) {
		return watch(createPath(pathFilter), listener);
	}

	public Subscription watch(ObservableConfigPath path, Consumer<ObservableConfigEvent> listener) {
		return theListeners.add(new InternalObservableConfigListener(path, listener), true)::run;
	}

	public ObservableCollection<? extends ObservableConfig> getAllContent() {
		return getContent(ANY_NAME);
	}

	public ObservableCollection<? extends ObservableConfig> getContent(String path) {
		return getContent(createPath(path));
	}

	public ObservableCollection<? extends ObservableConfig> getContent(ObservableConfigPath path) {
		if (path.getElements().size() == 1 && !path.getLastElement().getName().equals(MULTI_PATH)) {
			if (path.getLastElement().getName().equals(ANY_NAME))
				return new FullObservableConfigContent<>(this, getType());
			else
				return new SimpleObservableConfigContent<>(this, TYPE, path.getLastElement());
		} else
			return new ObservableConfigContent<>(this, TYPE, path);
	}

	public SettableValue<String> observeValue() {
		return observeValue(Format.TEXT);
	}

	public <T> SettableValue<T> observeValue(Format<T> format) {
		return observeValue((ObservableConfigPath) null, format);
	}

	public SettableValue<String> observeValue(String path) {
		return observeValue(createPath(path));
	}

	public SettableValue<String> observeValue(ObservableConfigPath path) {
		return observeValue(path, TypeTokens.get().STRING, Format.TEXT);
	}

	public <T> SettableValue<T> observeValue(String path, TypeToken<T> type, Format<T> format) {
		return observeValue(createPath(path), type, format);
	}

	public <T> SettableValue<T> observeValue(ObservableConfigPath path, TypeToken<T> type, Format<T> format) {
		return new ObservableConfigValue<T>(type, this, path, format);
	}

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

	public long getStamp(boolean structuralOnly) {
		return structuralOnly ? theStructureModCount : theModCount;
	}

	public ObservableConfig getChild(String path, boolean createIfAbsent) {
		return getChild(createPath(path), createIfAbsent);
	}

	public ObservableConfig getChild(ObservableConfigPath path, boolean createIfAbsent) {
		try (Transaction t = lock(createIfAbsent, null)) {
			ObservableConfig ret = null;
			List<ObservableConfig> eventPath = new ArrayList<>(path.getElements().size());
			for (ObservableConfigPathElement el : path.getElements()) {
				String pathName = el.getName();
				if (pathName.equals(ANY_NAME) || pathName.equals(MULTI_PATH))
					throw new IllegalArgumentException("Variable paths not allowed for getChild");
				ObservableConfig found = null;
				for (ObservableConfig config : theContent) {
					if (config.getName().equals(pathName)) {
						found = config;
						break;
					}
				}
				if (found == null) {
					if (createIfAbsent)
						found = addChild(pathName);
					else
						return null;
				}
				eventPath.add(found);
				ret = found;
			}
			return ret;
		}
	}

	public ObservableConfig addChild(String name) {
		try (Transaction t = lock(true, true, null)) {
			ElementId el = theContent.addElement(null, false).getElementId();
			ObservableConfig child = createChild(el, name, theLocking, null);
			theContent.mutableElement(el).set(child);
			fire(CollectionChangeType.add, Arrays.asList(child));
			return child;
		}
	}

	public ObservableConfig setValue(String value) {
		try (Transaction t = lock(true, false, null)) {
			theValue = value;
			fire(CollectionChangeType.set, Collections.emptyList());
		}
		return this;
	}

	public ObservableConfig set(String path, String value) {
		getChild(path, true).setValue(value);
		return this;
	}

	protected final Object getCurrentCause() {}

	protected void fire(CollectionChangeType eventType, List<ObservableConfig> relativePath) {
		_fire(eventType, relativePath, getCurrentCause());
	}

	private void _fire(CollectionChangeType eventType, List<ObservableConfig> relativePath, Object cause) {
		theModCount++;
		if (eventType != CollectionChangeType.set)
			theStructureModCount++;
		ObservableConfigEvent event = new ObservableConfigEvent(eventType, relativePath, getCurrentCause());
		try (Transaction t = Causable.use(event)) {
			theListeners.forEach(intL -> {
				if (intL.path == null || intL.path.matches(relativePath))
					intL.listener.accept(event);
			});
		}
		if (theParent != null)
			theParent.fire(eventType, addToList(this, relativePath));
	}

	private static List<ObservableConfig> addToList(ObservableConfig c, List<ObservableConfig> list) {
		ObservableConfig[] array = new ObservableConfig[list.size() + 1];
		array[0] = c;
		for (int i = 0; i < list.size(); i++)
			array[i + 1] = list.get(i);
		return Arrays.asList(array);
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

	protected static class ObservableConfigValue<T> implements SettableValue<T> {
		private final TypeToken<T> theType;
		private final ObservableConfig theRoot;
		private final ObservableConfigPath thePath;
		private final ObservableConfig[] thePathElements;
		private final Format<T> theFormat;

		public ObservableConfigValue(TypeToken<T> type, ObservableConfig root, ObservableConfigPath path, Format<T> format) {
			theType = type;
			theRoot = root;
			thePath = path;
			thePathElements = new ObservableConfig[path.getElements().size()];
			// TODO populate the elements
			theFormat = format;
		}

		@Override
		public TypeToken<T> getType() {
			return theType;
		}
	}

	protected static abstract class AbstractObservableConfigContent<C extends ObservableConfig> implements ObservableCollection<C> {
		private final ObservableConfig theConfig;
		private final TypeToken<C> theType;

		public AbstractObservableConfigContent(ObservableConfig config, TypeToken<C> type) {
			theConfig = config;
			theType = type;
		}

		public ObservableConfig getConfig() {
			return theConfig;
		}

		@Override
		public TypeToken<C> getType() {
			return theType;
		}

		@Override
		public boolean isLockSupported() {
			return theConfig.isLockSupported();
		}

		@Override
		public boolean isContentControlled() {
			return false;
		}

		@Override
		public long getStamp(boolean structuralOnly) {
			return theConfig.getStamp(structuralOnly);
		}

		@Override
		public CollectionElement<C> getElement(ElementId id) {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public MutableCollectionElement<C> mutableElement(ElementId id) {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public Transaction lock(boolean write, boolean structural, Object cause) {
			return theConfig.lock(write, structural, cause);
		}

		@Override
		public Transaction tryLock(boolean write, boolean structural, Object cause) {
			return theConfig.tryLock(write, structural, cause);
		}

		@Override
		public Equivalence<? super C> equivalence() {
			return Equivalence.DEFAULT;
		}
	}

	protected static class FullObservableConfigContent<C extends ObservableConfig> extends AbstractObservableConfigContent<C> {
		public FullObservableConfigContent(ObservableConfig config, TypeToken<C> type) {
			super(config, type);
		}
	}

	protected static class SimpleObservableConfigContent<C extends ObservableConfig> extends AbstractObservableConfigContent<C> {
		private final ObservableConfigPathElement thePathElement;

		public SimpleObservableConfigContent(ObservableConfig config, TypeToken<C> type, ObservableConfigPathElement pathEl) {
			super(config, type);
			thePathElement = pathEl;
		}
	}

	protected static class ObservableConfigContent<C extends ObservableConfig> extends AbstractObservableConfigContent<C> {
		private final ObservableConfigPath thePath;

		public ObservableConfigContent(ObservableConfig config, TypeToken<C> type, ObservableConfigPath path) {
			super(config, type);
			thePath = path;
		}
	}
}
