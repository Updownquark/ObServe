package org.observe.config;

import java.text.ParseException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.ObservableValueEvent;
import org.observe.Observer;
import org.observe.SettableValue;
import org.observe.Subscription;
import org.observe.collect.CollectionChangeType;
import org.observe.collect.Equivalence;
import org.observe.collect.ObservableCollection;
import org.observe.util.TypeTokens;
import org.qommons.Causable;
import org.qommons.StructuredTransactable;
import org.qommons.Transaction;
import org.qommons.ValueHolder;
import org.qommons.collect.BetterList;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.CollectionLockingStrategy;
import org.qommons.collect.ElementId;
import org.qommons.collect.ListenerList;
import org.qommons.collect.MutableCollectionElement;
import org.qommons.collect.MutableCollectionElement.StdMsg;
import org.qommons.io.Format;
import org.qommons.tree.BetterTreeList;

import com.google.common.reflect.TypeToken;

/**
 * <p>
 * A hierarchical structure of configuration elements. Each element has a name, a value (string), and any number of child configuration
 * elements. It is intended to provide an easy injection route for configuration data files (e.g. XML) into an application. Utilities are
 * provided to serialize and deserialize to persistence on various triggers.
 * </p>
 * <p>
 * The structure fires detailed events when it is changed, and utilities are provided to monitor individual configuration values and
 * configurable collections of child configurations as standard observable structures.
 * </p>
 */
public class ObservableConfig implements StructuredTransactable {
	public static final char PATH_SEPARATOR = '/';
	public static final String PATH_SEPARATOR_STR = "" + PATH_SEPARATOR;
	public static final String EMPTY_PATH = "".intern();
	public static final String ANY_NAME = "*".intern();
	public static final String ANY_DEPTH = "**".intern();
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
			else if (pathIter.hasNext() && !ANY_DEPTH.equals(getLastElement().getName()))
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
					str.append(PATH_SEPARATOR);
				str.append(el.toString());
			}
			return theElements.toString();
		}
	}

	public static class ObservableConfigPathElement {
		private final String theName;
		private final Map<String, String> theAttributes;
		private final boolean isMulti;

		protected ObservableConfigPathElement(String name, Map<String, String> attributes, boolean multi) {
			theName = name;
			theAttributes = attributes;
			isMulti = multi;
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

		@Override
		public int hashCode() {
			return Objects.hash(theName, theAttributes, isMulti);
		}

		@Override
		public boolean equals(Object obj) {
			if (obj == this)
				return true;
			else if (!(obj instanceof ObservableConfigPathElement))
				return false;
			ObservableConfigPathElement other = (ObservableConfigPathElement) obj;
			return theName.equals(other.theName) && Objects.equals(theAttributes, other.theAttributes) && isMulti == other.isMulti;
		}

		@Override
		public String toString() {
			if (theAttributes.isEmpty() && !isMulti)
				return theName;
			StringBuilder str = new StringBuilder(theName);
			if (isMulti)
				str.append(ANY_NAME);
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

	public static class ObservableConfigPathBuilder {
		private final ObservableConfig theTarget;
		private final List<ObservableConfigPathElement> thePath;
		private final String theName;
		private Map<String, String> theAttributes;
		private boolean isMulti;
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

		public ObservableConfigPathBuilder multi() {
			isMulti = true;
			return this;
		}

		protected void seal() {
			if (isUsed)
				throw new IllegalStateException("This class cannot be used twice");
			isUsed = true;
			thePath.add(new ObservableConfigPathElement(theName,
				theAttributes == null ? Collections.emptyMap() : Collections.unmodifiableMap(theAttributes), isMulti));
		}

		public ObservableConfigPathBuilder andThen(String name) {
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
	private final ValueHolder<Causable> theRootCausable;
	private final String theName;
	private String theValue;
	private final BetterList<ObservableConfig> theContent;
	private final ListenerList<InternalObservableConfigListener> theListeners;
	private long theStructureModCount;
	private long theModCount;

	protected ObservableConfig(ObservableConfig parent, ElementId parentContentRef, String name, CollectionLockingStrategy locking,
		ValueHolder<Causable> rootCause, String value) {
		theParent = parent;
		theParentContentRef = parentContentRef;
		theLocking = locking;
		theRootCausable = rootCause;
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
		for (int i = 1; i < split.length; i++) {
			boolean multi = split[i].length() > 0 && split[i].charAt(split[i].length() - 1) == ANY_NAME.charAt(0);
			if (multi)
				split[i] = split[i].substring(0, split[i].length() - 1);
			builder = builder.andThen(split[i]);
			if (multi)
				builder.multi();
		}
		return builder.build();
	}

	protected TypeToken<? extends ObservableConfig> getType() {
		return TYPE;
	}

	protected ObservableConfig createChild(ElementId parentContentRef, String name, CollectionLockingStrategy locking,
		ValueHolder<Causable> rootCause, String value) {
		return new ObservableConfig(this, parentContentRef, name, locking, rootCause, value);
	}

	public Observable<ObservableConfigEvent> watch(String pathFilter) {
		return watch(createPath(pathFilter));
	}

	public Observable<ObservableConfigEvent> watch(ObservableConfigPath path) {
		return new ObservableConfigChangesObservable(this, path);
	}

	public ObservableCollection<? extends ObservableConfig> getAllContent() {
		return getContent(ANY_NAME);
	}

	public ObservableCollection<? extends ObservableConfig> getContent(String path) {
		return getContent(createPath(path));
	}

	public ObservableCollection<? extends ObservableConfig> getContent(ObservableConfigPath path) {
		if (path.getElements().size() == 1) {
			if (path.getLastElement().getName().equals(ANY_NAME))
				return new FullObservableConfigContent<>(this, getType());
			else
				return new SimpleObservableConfigContent<>(this, TYPE, path.getLastElement());
		} else
			return new ObservableConfigContent<>(this, TYPE, path);
	}

	public SettableValue<String> observeValue() {
		return observeValue(TypeTokens.get().STRING, Format.TEXT);
	}

	public <T> SettableValue<T> observeValue(TypeToken<T> type, Format<T> format) {
		return observeValue((ObservableConfigPath) null, type, format);
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
		return withCause(theContent.lock(write, structural, cause), cause);
	}

	@Override
	public Transaction tryLock(boolean write, boolean structural, Object cause) {
		return withCause(theContent.tryLock(write, structural, cause), cause);
	}

	private Transaction withCause(Transaction t, Object cause) {
		if (t == null)
			return null;
		boolean causeIsRoot = theRootCausable.get() == null;
		if (causeIsRoot) {
			if (cause instanceof Causable) {
				theRootCausable.accept((Causable) cause);
				return () -> {
					theRootCausable.accept(null);
					t.close();
				};
			} else {
				Causable synCause = Causable.simpleCause(cause);
				Transaction causeT = Causable.use(synCause);
				theRootCausable.accept(synCause);
				return () -> {
					causeT.close();
					theRootCausable.accept(null);
					t.close();
				};
			}
		} else
			return t;
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
			for (ObservableConfigPathElement el : path.getElements()) {
				ObservableConfig found = ret.getChild(el, createIfAbsent);
				if (found == null)
					return null;
				ret = found;
			}
			return ret;
		}
	}

	protected ObservableConfig getChild(ObservableConfigPathElement el, boolean createIfAbsent) {
		String pathName = el.getName();
		if (pathName.equals(ANY_NAME) || pathName.equals(ANY_DEPTH))
			throw new IllegalArgumentException("Variable paths not allowed for getChild");
		ObservableConfig found = null;
		for (ObservableConfig config : theContent) {
			if (config.getName().equals(pathName)) {
				found = config;
				break;
			}
		}
		if (found == null && createIfAbsent)
			found = addChild(pathName);
		return found;
	}

	public ObservableConfig addChild(String name) {
		try (Transaction t = lock(true, true, null)) {
			ElementId el = theContent.addElement(null, false).getElementId();
			ObservableConfig child = createChild(el, name, theLocking, theRootCausable, null);
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

	public void remove() {
		try (Transaction t = lock(true, null)) {
			if (!theParentContentRef.isPresent())
				return;
			theParent.theContent.mutableElement(theParentContentRef).remove();
			fire(CollectionChangeType.remove, Collections.emptyList());
		}
	}

	protected final Object getCurrentCause() {
		return theRootCausable.get();
	}

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
				if (intL.path == null || intL.path.matches(relativePath)) {
					if (relativePath.isEmpty() && eventType == CollectionChangeType.remove)
						intL.listener.onCompleted(event);
					else
						intL.listener.onNext(event);
				}
			});
		}
		if (theParent != null && theParentContentRef.isPresent())
			theParent.fire(eventType, addToList(this, relativePath));
	}

	private static List<ObservableConfig> addToList(ObservableConfig c, List<ObservableConfig> list) {
		ObservableConfig[] array = new ObservableConfig[list.size() + 1];
		array[0] = c;
		for (int i = 0; i < list.size(); i++)
			array[i + 1] = list.get(i);
		return Arrays.asList(array);
	}

	public Subscription persistOnShutdown() {
		return persistWhen(Observable.onVmShutdown());
	}

	public Subscription persistEvery(Duration interval) {
		return persistWhen(Observable.<Void> every(Duration.ZERO, interval, null, d -> null));
	}

	public Subscription persistOnChange() {
		return persistWhen(watch(buildPath(ANY_DEPTH).build()));
	}

	public Subscription persistWhen(Observable<?> observable) {}

	private static class InternalObservableConfigListener {
		final ObservableConfigPath path;
		final Observer<? super ObservableConfigEvent> listener;

		InternalObservableConfigListener(ObservableConfigPath path, Observer<? super ObservableConfigEvent> observer) {
			this.path = path;
			this.listener = observer;
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

	private static class ObservableConfigChangesObservable implements Observable<ObservableConfigEvent> {
		private final ObservableConfig theConfig;
		private final ObservableConfigPath thePath;

		ObservableConfigChangesObservable(ObservableConfig config, ObservableConfigPath path) {
			theConfig = config;
			thePath = path;
		}

		@Override
		public Subscription subscribe(Observer<? super ObservableConfigEvent> observer) {
			return theConfig.theListeners.add(new InternalObservableConfigListener(thePath, observer), true)::run;
		}

		@Override
		public boolean isSafe() {
			return theConfig.isLockSupported();
		}

		@Override
		public Transaction lock() {
			return theConfig.lock(false, null);
		}

		@Override
		public Transaction tryLock() {
			return theConfig.tryLock(false, null);
		}
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
			for (ObservableConfigPathElement el : path.getElements()) {
				if (el.isMulti() || el.getName().equals(ANY_NAME) || el.getName().equals(ANY_DEPTH))
					throw new IllegalArgumentException("Cannot use observeValue with a variable path");
			}
			theFormat = format;
			thePathElements = new ObservableConfig[path.getElements().size()];
		}

		@Override
		public TypeToken<T> getType() {
			return theType;
		}

		@Override
		public T get() {
			try (Transaction t = lock()) {
				if (!resolvePath(false))
					return null;
				String value = thePathElements[thePathElements.length - 1].getValue();
				if (value == null)
					return null;
				return parse(value);
			}
		}

		private boolean resolvePath(boolean createIfAbsent) {
			ObservableConfig parent = theRoot;
			for (int i = 0; i < thePathElements.length; i++) {
				ObservableConfig child;
				if (thePathElements[i] != null && thePathElements[i].theParentContentRef.isPresent()) {
					child = thePathElements[i];
					continue;
				}
				child = parent.getChild(thePath.getElements().get(i), createIfAbsent);
				if (child == null)
					return false;
				thePathElements[i] = parent = child;
			}
			return true;
		}

		private T parse(String valueStr) {
			try {
				return theFormat.parse(valueStr);
			} catch (ParseException e) {
				System.err.println("Could not parse value " + this + ": " + e.getMessage());
				return null;
			}
		}

		@Override
		public Observable<ObservableValueEvent<T>> noInitChanges() {
			// TODO Auto-generated method stub
		}

		@Override
		public <V extends T> T set(V value, Object cause) throws IllegalArgumentException, UnsupportedOperationException {
			try (Transaction t = theRoot.lock(true, cause)) {
				String msg = isAcceptable(value);
				if (msg != null)
					throw new IllegalArgumentException(msg);
				if (resolvePath(true))
					throw new UnsupportedOperationException(StdMsg.NOT_FOUND);
				String valueStr = theFormat.format(value);
				String oldValueStr = thePathElements[thePathElements.length - 1].getValue();
				thePathElements[thePathElements.length - 1].setValue(valueStr);
				return parse(oldValueStr);
			}
		}

		@Override
		public <V extends T> String isAcceptable(V value) {
			return TypeTokens.get().isInstance(theType, value) ? null : StdMsg.BAD_TYPE;
		}

		@Override
		public ObservableValue<String> isEnabled() {
			// TODO Auto-generated method stub
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
		}

		@Override
		public MutableCollectionElement<C> mutableElement(ElementId id) {
			// TODO Auto-generated method stub
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
