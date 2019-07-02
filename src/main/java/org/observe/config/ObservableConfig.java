package org.observe.config;

import java.text.ParseException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntConsumer;

import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.ObservableValueEvent;
import org.observe.Observer;
import org.observe.SettableValue;
import org.observe.Subscription;
import org.observe.collect.CollectionChangeType;
import org.observe.collect.Equivalence;
import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableCollectionEvent;
import org.observe.collect.ObservableValueSet;
import org.observe.collect.ValueCreator;
import org.observe.util.EntityReflector;
import org.observe.util.TypeTokens;
import org.qommons.Causable;
import org.qommons.StructuredTransactable;
import org.qommons.Transaction;
import org.qommons.ValueHolder;
import org.qommons.collect.BetterCollection;
import org.qommons.collect.BetterList;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.CollectionLockingStrategy;
import org.qommons.collect.ElementId;
import org.qommons.collect.ListenerList;
import org.qommons.collect.MutableCollectionElement;
import org.qommons.collect.MutableCollectionElement.StdMsg;
import org.qommons.io.Format;
import org.qommons.tree.BetterTreeList;

import com.google.common.reflect.Invokable;
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

		public ObservableConfigPath getLast() {
			if (theElements.size() == 1)
				return this;
			return new ObservableConfigPath(theElements.subList(theElements.size() - 1, theElements.size()));
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

		public boolean matchedBefore(ObservableConfig config, ObservableConfigEvent change) {
			if (!theName.equals(ANY_NAME)) {
				if (change.relativePath.size() == 1) {
					if (!theName.equals(change.oldName))
						return false;
				} else if (!theName.equals(config.getName()))
					return false;
			}
			try (Transaction t = config.lock(false, null)) {
				for (Map.Entry<String, String> attr : theAttributes.entrySet()) {
					boolean found = false;

					if (change.relativePath.size() == 1 && change.oldName.equals(attr.getKey()))
						found = attr.getValue() == null || attr.getValue().equals(change.oldValue);
					if (!found) {
						for (ObservableConfig child : config.theContent) {
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
		public final String oldName;
		public final String oldValue;

		public ObservableConfigEvent(CollectionChangeType changeType, String oldName, String oldValue, List<ObservableConfig> relativePath,
			Object cause) {
			super(cause);
			this.changeType = changeType;
			this.relativePath = relativePath;
			this.oldName = oldName;
			this.oldValue = oldValue;
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

		public ObservableConfigEvent asFromChild() {
			return new ObservableConfigEvent(changeType, oldName, oldValue, relativePath.subList(1, relativePath.size()), getCause());
		}
	}

	public static interface ObservableConfigPersistence<E extends Exception> {
		void persist(ObservableConfig config) throws E;
	}

	private final ObservableConfig theParent;
	private final ElementId theParentContentRef;
	private final CollectionLockingStrategy theLocking;
	private final ValueHolder<Causable> theRootCausable;
	private String theName;
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

	ElementId getParentChildRef() {
		return theParentContentRef;
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
		} else {
			ObservableConfigPath last = path.getLast();
			TypeToken<ObservableConfig> type = (TypeToken<ObservableConfig>) getType();
			TypeToken<ObservableCollection<ObservableConfig>> collType = TypeTokens.get().keyFor(ObservableCollection.class)
				.getCompoundType(type);
			return ObservableCollection.flattenValue(
				observeDescendant(path.getParent()).map(collType, p -> (ObservableCollection<ObservableConfig>) p.getContent(last)));
		}
	}

	public ObservableValue<? extends ObservableConfig> observeDescendant(ObservableConfigPath path) {
		return new ObservableConfigChild<>(getType(), this, path);
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
		return new ObservableConfigValue<>(type, this, path, config -> {
			if (config.getValue() == null)
				return null;
			try {
				return format.parse(config.getValue());
			} catch (ParseException e) {
				System.err.println("Could not parse value " + this + ": " + e.getMessage());
				return null;
			}
		}, (config, val) -> config.setValue(format.format(val)));
	}

	public <T> SettableValue<T> observeValue(ObservableConfigPath path, TypeToken<T> type, Function<ObservableConfig, ? extends T> parser,
		BiConsumer<ObservableConfig, ? super T> format) {
		return new ObservableConfigValue<T>(type, this, path, parser, format);
	}

	public <T> ObservableValueSet<T> observeValues(ObservableConfigPath path, TypeToken<T> type, Format<T> format) {
		return observeValues(path, type, config -> {
			if (config.getValue() == null)
				return null;
			try {
				return format.parse(config.getValue());
			} catch (ParseException e) {
				System.err.println("Could not parse value " + this + ": " + e.getMessage());
				return null;
			}
		}, (config, val) -> config.setValue(format.format(val)));
	}

	public <T> ObservableValueSet<T> observeValues(ObservableConfigPath path, TypeToken<T> type,
		Function<ObservableConfig, ? extends T> parser, BiConsumer<ObservableConfig, ? super T> format, Observable<?> until) {
		ObservableCollection<? extends ObservableConfig> configs;
		if (path.getElements().size() == 1)
			configs = getContent(path.getLastElement().getName());
		else {
			TypeToken<ObservableCollection<? extends ObservableConfig>> configListType = (TypeToken<ObservableCollection<? extends ObservableConfig>>) (TypeToken<?>) TypeTokens
				.get().keyFor(ObservableCollection.class).getCompoundType(getType());
			configs = ObservableCollection.flattenValue(observeDescendant(path.getParent()).map(configListType,
				descendant -> descendant.getContent(path.getLastElement().getName())));
		}
		return new ObservableConfigValues<>(this, configs, type, parser, format, until);
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
			if (el.matches(config)) {
				found = config;
				break;
			}
		}
		if (found == null && createIfAbsent) {
			found = addChild(pathName, ch -> {
				for (Map.Entry<String, String> attr : el.getAttributes().entrySet())
					ch.addChild(attr.getKey(), atCh -> atCh.setValue(attr.getValue()));
			});
		}
		return found;
	}

	public ObservableConfig addChild(String name) {
		return addChild(name, null);
	}

	public ObservableConfig addChild(String name, Consumer<ObservableConfig> preAddMod) {
		try (Transaction t = lock(true, true, null)) {
			ElementId el = theContent.addElement(null, false).getElementId();
			ObservableConfig child = createChild(el, name, theLocking, theRootCausable, null);
			theContent.mutableElement(el).set(child);
			if (preAddMod != null)
				preAddMod.accept(child);
			fire(CollectionChangeType.add, Arrays.asList(child), name, null);
			return child;
		}
	}

	public ObservableConfig setName(String name) {
		if (name == null)
			throw new NullPointerException("Name must not be null");
		try (Transaction t = lock(true, false, null)) {
			String oldName = theName;
			theName = name;
			fire(CollectionChangeType.set, Collections.emptyList(), oldName, theValue);
		}
		return this;
	}

	public ObservableConfig setValue(String value) {
		try (Transaction t = lock(true, false, null)) {
			String oldValue = theValue;
			theValue = value;
			fire(CollectionChangeType.set, Collections.emptyList(), theName, oldValue);
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
			fire(CollectionChangeType.remove, Collections.emptyList(), theName, theValue);
		}
	}

	protected final Object getCurrentCause() {
		return theRootCausable.get();
	}

	protected void fire(CollectionChangeType eventType, List<ObservableConfig> relativePath, String oldName, String oldValue) {
		_fire(eventType, relativePath, oldName, oldValue, getCurrentCause());
	}

	private void _fire(CollectionChangeType eventType, List<ObservableConfig> relativePath, String oldName, String oldValue, Object cause) {
		theModCount++;
		if (eventType != CollectionChangeType.set && relativePath.isEmpty())
			theStructureModCount++;
		ObservableConfigEvent event = new ObservableConfigEvent(eventType, oldName, oldValue, relativePath, getCurrentCause());
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
			theParent.fire(eventType, addToList(this, relativePath), oldName, oldValue);
	}

	private static List<ObservableConfig> addToList(ObservableConfig c, List<ObservableConfig> list) {
		ObservableConfig[] array = new ObservableConfig[list.size() + 1];
		array[0] = c;
		for (int i = 0; i < list.size(); i++)
			array[i + 1] = list.get(i);
		return Arrays.asList(array);
	}

	public <E extends Exception> Subscription persistOnShutdown(ObservableConfigPersistence<E> persistence,
		Consumer<? super Exception> onException) {
		return persistWhen(Observable.onVmShutdown(), persistence, onException);
	}

	public <E extends Exception> Subscription persistEvery(Duration interval, ObservableConfigPersistence<E> persistence,
		Consumer<? super Exception> onException) {
		return persistWhen(Observable.<Void> every(Duration.ZERO, interval, null, d -> null), persistence, onException);
	}

	public <E extends Exception> Subscription persistOnChange(ObservableConfigPersistence<E> persistence,
		Consumer<? super Exception> onException) {
		return persistWhen(watch(buildPath(ANY_DEPTH).build()), persistence, onException);
	}

	public <E extends Exception> Subscription persistWhen(Observable<?> observable, ObservableConfigPersistence<E> persistence,
		Consumer<? super Exception> onException) {
		return observable.subscribe(new Observer<Object>() {
			private long theLastStamp = theModCount;

			@Override
			public <V> void onNext(V value) {
				tryPersist(false);
			}

			@Override
			public <V> void onCompleted(V value) {
				tryPersist(true);
			}

			private void tryPersist(boolean waitForLock) {
				if (theModCount == theLastStamp)
					return; // No changes, don't re-persist
				Transaction lock = waitForLock ? lock(false, null) : tryLock(false, null);
				if (lock == null)
					return;
				try {
					theLastStamp = theModCount;
					persistence.persist(ObservableConfig.this);
				} catch (Exception ex) {
					onException.accept(ex);
				}
			}
		});
	}

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

	protected static class ObservableConfigChild<C extends ObservableConfig> implements ObservableValue<C> {
		private final TypeToken<C> theType;
		private final ObservableConfig theRoot;
		private final ObservableConfigPath thePath;
		private final ObservableConfig[] thePathElements;
		private final Subscription[] thePathElSubscriptions;
		private final ListenerList<Observer<? super ObservableValueEvent<C>>> theListeners;

		public ObservableConfigChild(TypeToken<C> type, ObservableConfig root, ObservableConfigPath path) {
			theType = type;
			theRoot = root;
			thePath = path;
			for (ObservableConfigPathElement el : path.getElements()) {
				if (el.isMulti() || el.getName().equals(ANY_NAME) || el.getName().equals(ANY_DEPTH))
					throw new IllegalArgumentException("Cannot use observeValue with a variable path");
			}
			thePathElements = new ObservableConfig[path.getElements().size() + 1];
			thePathElements[0] = theRoot;
			thePathElSubscriptions = new Subscription[thePathElements.length];
			theListeners = ListenerList.build().withInUse(this::setInUse).build();
		}

		ObservableConfig getRoot() {
			return theRoot;
		}

		void setInUse(boolean inUse) {
			try (Transaction t = theRoot.lock(false, null)) {
				if (inUse) {
					watchPathElement(0);
					if (resolvePath(1, false, i -> watchPathElement(i)))
						watchTerminal();
				} else {
					invalidate(0);
					for (int i = thePathElSubscriptions.length - 1; i >= 0; i--) {
						thePathElSubscriptions[i].unsubscribe();
						thePathElSubscriptions[i] = null;
					}
				}
			}
		}

		private void pathChanged(int pathIndex, CollectionChangeType type, ObservableConfig newValue) {
			boolean reCheck = false;
			switch (type) {
			case add:
				// This can affect the value if the new value matches the path and appears before the currently-used config
				if (newValue.theParentContentRef.compareTo(thePathElements[pathIndex].theParentContentRef) < 0
					&& thePath.getElements().get(pathIndex).matches(newValue))
					reCheck = true; // The new element needs to replace the current element at the path index
				break;
			case remove:
			case set:
				if (newValue == thePathElements[pathIndex])
					reCheck = true;
				break;
			}
			if (reCheck) {
				invalidate(pathIndex);
				if (resolvePath(pathIndex, false, i -> watchPathElement(i)))
					watchTerminal();
			}
		}

		private void watchPathElement(int pathIndex) {
			thePathElSubscriptions[pathIndex] = thePathElements[pathIndex].getAllContent()
				.onChange(evt -> pathChanged(pathIndex + 1, evt.getType(), evt.getNewValue()));
		}

		private void watchTerminal() {
			int lastIdx = thePathElements.length - 1;
			thePathElSubscriptions[thePathElements.length] = thePathElements[lastIdx].watch(EMPTY_PATH).act(evt -> {
				fire(createChangeEvent((C) thePathElements[lastIdx], (C) thePathElements[lastIdx], evt));
			});
		}

		private void fire(ObservableValueEvent<C> event) {
			theListeners.forEach(//
				listener -> listener.onNext(event));
		}

		@Override
		public TypeToken<C> getType() {
			return theType;
		}

		@Override
		public C get() {
			try (Transaction t = lock()) {
				if (!resolvePath(1, false, null))
					return null;
				return (C) thePathElements[thePathElements.length - 1];
			}
		}

		private void invalidate(int startIndex) {
			for (int i = thePathElSubscriptions.length - 1; i >= startIndex; i--) {
				if (thePathElSubscriptions[i] != null) {
					thePathElSubscriptions[i].unsubscribe();
					thePathElSubscriptions[i] = null;
				}
			}
			for (int i = thePathElements.length - 1; i >= startIndex; i--)
				thePathElements[i] = null;
		}

		boolean resolvePath(int startIndex, boolean createIfAbsent, IntConsumer onResolution) {
			ObservableConfig parent = thePathElements[startIndex - 1];
			boolean resolved = true;
			int i;
			for (i = startIndex; i < thePathElements.length; i++) {
				ObservableConfig child;
				if (thePathElements[i] != null && thePathElements[i].theParentContentRef.isPresent()) {
					child = thePathElements[i];
					continue;
				}
				child = parent.getChild(thePath.getElements().get(i), createIfAbsent);
				if (child == null) {
					resolved = false;
					break;
				} else {
					thePathElements[i] = parent = child;
					onResolution.accept(i);
				}
			}
			if (!resolved)
				Arrays.fill(thePathElements, i, thePathElements.length, null);
			return true;
		}

		@Override
		public Observable<ObservableValueEvent<C>> noInitChanges() {
			return new Observable<ObservableValueEvent<C>>() {
				@Override
				public Subscription subscribe(Observer<? super ObservableValueEvent<C>> observer) {
					return theListeners.add(observer, true)::run;
				}

				@Override
				public boolean isSafe() {
					return theRoot.isLockSupported();
				}

				@Override
				public Transaction lock() {
					return theRoot.lock(false, null);
				}

				@Override
				public Transaction tryLock() {
					return theRoot.tryLock(false, null);
				}
			};
		}
	}

	protected static class ObservableConfigValue<T> implements SettableValue<T> {
		private final ObservableConfigChild<ObservableConfig> theConfigChild;
		private final TypeToken<T> theType;
		private final Function<ObservableConfig, ? extends T> theParser;
		private final BiConsumer<ObservableConfig, ? super T> theFormat;

		public ObservableConfigValue(TypeToken<T> type, ObservableConfig root, ObservableConfigPath path,
			Function<ObservableConfig, ? extends T> parser, BiConsumer<ObservableConfig, ? super T> format) {
			theConfigChild = new ObservableConfigChild<>(ObservableConfig.TYPE, root, path);
			theType = type;
			theParser = parser;
			theFormat = format;
		}

		@Override
		public TypeToken<T> getType() {
			return theType;
		}

		@Override
		public T get() {
			try (Transaction t = lock()) {
				ObservableConfig config = theConfigChild.get();
				return parse(config);
			}
		}

		private T parse(ObservableConfig config) {
			return theParser.apply(config);
		}

		@Override
		public Observable<ObservableValueEvent<T>> noInitChanges() {
			return theConfigChild.noInitChanges().map(evt -> createChangeEvent(parse(evt.getOldValue()), parse(evt.getNewValue()), evt));
		}

		@Override
		public <V extends T> T set(V value, Object cause) throws IllegalArgumentException, UnsupportedOperationException {
			try (Transaction t = theConfigChild.getRoot().lock(true, cause)) {
				String msg = isAcceptable(value);
				if (msg != null)
					throw new IllegalArgumentException(msg);
				theConfigChild.resolvePath(1, true, null);
				T oldValue = parse(theConfigChild.get());
				theFormat.accept(theConfigChild.get(), value);
				return oldValue;
			}
		}

		@Override
		public <V extends T> String isAcceptable(V value) {
			return TypeTokens.get().isInstance(theType, value) ? null : StdMsg.BAD_TYPE;
		}

		@Override
		public ObservableValue<String> isEnabled() {
			return SettableValue.ALWAYS_ENABLED;
		}
	}

	protected static class ObservableConfigValues<T> implements ObservableValueSet<T> {
		private final ObservableConfig theRoot;
		private final ObservableCollection<? extends ObservableConfig> theConfigValues;
		private final TypeToken<T> theType;
		private final Function<ObservableConfig, ? extends T> theParser;
		private final BiConsumer<ObservableConfig, ? super T> theFormat;

		private final ObservableCollection<T> theValues;
		private final EntityReflector<T> theReflector;

		public ObservableConfigValues(ObservableConfig root, ObservableCollection<? extends ObservableConfig> configValues,
			TypeToken<T> type, Function<ObservableConfig, ? extends T> parser, BiConsumer<ObservableConfig, ? super T> format,
			Observable<?> until) {
			theRoot = root;
			theConfigValues = configValues;
			theType = type;
			theParser = parser;
			theFormat = format;
			theReflector = new EntityReflector<>(type);

			theValues = ((ObservableCollection<ObservableConfig>) configValues).flow().map(type, parser).collectActive(until);
		}

		@Override
		public ObservableCollection<? extends T> getValues() {
			return theValues;
		}

		@Override
		public ValueCreator<T> create() {
			return new ValueCreator<T>() {
				private final Map<String, Object> theFields = new LinkedHashMap<>();

				@Override
				public ValueCreator<T> with(String fieldName, Object value) throws IllegalArgumentException {
					int fieldIndex = theReflector.getFieldGetters().keySet().indexOf(fieldName);
					Invokable<? super T, ?> field = theReflector.getFieldGetters().get(fieldName);
					// TODO Auto-generated method stub
				}

				@Override
				public <F> ValueCreator<T> with(Function<? super T, F> field, F value)
					throws IllegalArgumentException, UnsupportedOperationException {
					// TODO Auto-generated method stub
				}

				@Override
				public CollectionElement<T> create() {
					// TODO Auto-generated method stub
				}
			};
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

		@Override
		public void clear() {
			try (Transaction t = getConfig().lock(true, null)) {
				ObservableConfig lastChild = getConfig().theContent.getLast();
				while (lastChild != null) {
					ObservableConfig nextLast = CollectionElement
						.get(getConfig().theContent.getAdjacentElement(lastChild.theParentContentRef, false));
					lastChild.remove();
					lastChild = nextLast;
				}
			}
		}

		@Override
		public int size() {
			return getConfig().theContent.size();
		}

		@Override
		public boolean isEmpty() {
			return getConfig().theContent.isEmpty();
		}

		@Override
		public CollectionElement<C> getElement(int index) {
			ObservableConfig child = getConfig().theContent.get(index);
			return new ConfigCollectionElement<>(child);
		}

		@Override
		public int getElementsBefore(ElementId id) {
			return getConfig().theContent.getElementsBefore(id);
		}

		@Override
		public int getElementsAfter(ElementId id) {
			return getConfig().theContent.getElementsAfter(id);
		}

		@Override
		public CollectionElement<C> getElement(C value, boolean first) {
			ObservableConfig config = CollectionElement.get(getConfig().theContent.getElement(value, first));
			return config == null ? null : new ConfigCollectionElement<>(config);
		}

		@Override
		public CollectionElement<C> getElement(ElementId id) {
			ObservableConfig config = CollectionElement.get(getConfig().theContent.getElement(id));
			return new ConfigCollectionElement<>(config);
		}

		@Override
		public CollectionElement<C> getTerminalElement(boolean first) {
			ObservableConfig config = CollectionElement.get(getConfig().theContent.getTerminalElement(first));
			return config == null ? null : new ConfigCollectionElement<>(config);
		}

		@Override
		public CollectionElement<C> getAdjacentElement(ElementId elementId, boolean next) {
			ObservableConfig config = CollectionElement.get(getConfig().theContent.getAdjacentElement(elementId, next));
			return config == null ? null : new ConfigCollectionElement<>(config);
		}

		@Override
		public MutableCollectionElement<C> mutableElement(ElementId id) {
			ObservableConfig config = CollectionElement.get(getConfig().theContent.getElement(id));
			return new MutableConfigCollectionElement<>(config, this);
		}

		@Override
		public String canAdd(C value, ElementId after, ElementId before) {
			return StdMsg.UNSUPPORTED_OPERATION;
		}

		@Override
		public CollectionElement<C> addElement(C value, ElementId after, ElementId before, boolean first)
			throws UnsupportedOperationException, IllegalArgumentException {
			throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
		}

		@Override
		public void setValue(Collection<ElementId> elements, C value) {
			throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
		}

		@Override
		public Subscription onChange(Consumer<? super ObservableCollectionEvent<? extends C>> observer) {
			return getConfig().watch(getConfig().createPath(ANY_NAME)).act(evt -> {
				C child = (C) evt.relativePath.get(0);
				C oldValue = evt.changeType == CollectionChangeType.add ? null : child;
				ObservableCollectionEvent<C> collEvt = new ObservableCollectionEvent<>(child.getParentChildRef(), getType(),
					child.getIndexInParent(), evt.changeType, oldValue, child, evt);
				observer.accept(collEvt);
			});
		}
	}

	private static class ConfigCollectionElement<C extends ObservableConfig> implements CollectionElement<C> {
		final ObservableConfig theConfig;

		ConfigCollectionElement(ObservableConfig config) {
			theConfig = config;
		}

		@Override
		public ElementId getElementId() {
			return theConfig.theParentContentRef;
		}

		@Override
		public C get() {
			return (C) theConfig;
		}

		@Override
		public int hashCode() {
			return theConfig.theParentContentRef.hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			return obj instanceof ConfigCollectionElement && ((ConfigCollectionElement<?>) obj).theConfig == theConfig;
		}

		@Override
		public String toString() {
			return theConfig.toString();
		}
	}

	private static class MutableConfigCollectionElement<C extends ObservableConfig> extends ConfigCollectionElement<C>
	implements MutableCollectionElement<C> {
		private final ObservableCollection<C> theCollection;

		MutableConfigCollectionElement(ObservableConfig config, ObservableCollection<C> collection) {
			super(config);
			theCollection = collection;
		}

		@Override
		public BetterCollection<C> getCollection() {
			return theCollection;
		}

		@Override
		public String isEnabled() {
			return StdMsg.UNSUPPORTED_OPERATION;
		}

		@Override
		public String isAcceptable(C value) {
			return StdMsg.UNSUPPORTED_OPERATION;
		}

		@Override
		public void set(C value) throws UnsupportedOperationException, IllegalArgumentException {
			throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
		}

		@Override
		public String canRemove() {
			return null;
		}

		@Override
		public void remove() throws UnsupportedOperationException {
			theConfig.remove();
		}
	}

	protected static class SimpleObservableConfigContent<C extends ObservableConfig> extends AbstractObservableConfigContent<C> {
		private final ObservableConfigPathElement thePathElement;

		public SimpleObservableConfigContent(ObservableConfig config, TypeToken<C> type, ObservableConfigPathElement pathEl) {
			super(config, type);
			thePathElement = pathEl;
		}

		@Override
		public int size() {
			try (Transaction t = getConfig().lock(false, null)) {
				return (int) getConfig().theContent.stream().filter(thePathElement::matches).count();
			}
		}

		@Override
		public boolean isEmpty() {
			try (Transaction t = getConfig().lock(false, null)) {
				return getConfig().theContent.stream().anyMatch(thePathElement::matches);
			}
		}

		@Override
		public CollectionElement<C> getElement(int index) {
			try (Transaction t = getConfig().lock(false, null)) {
				int i = 0;
				for (CollectionElement<ObservableConfig> el : getConfig().theContent.elements()) {
					if (thePathElement.matches(el.get())) {
						if (i == index)
							return new ConfigCollectionElement<>(el.get());
						i++;
					}
				}
				throw new IndexOutOfBoundsException(index + " of " + i);
			}
		}

		@Override
		public int getElementsBefore(ElementId id) {
			try (Transaction t = getConfig().lock(false, null)) {
				ObservableConfig config = CollectionElement.get(getConfig().theContent.getElement(id));
				if (config == null || !thePathElement.matches(config))
					throw new NoSuchElementException();
				int i = 0;
				for (CollectionElement<ObservableConfig> el : getConfig().theContent.elements()) {
					if (thePathElement.matches(el.get())) {
						if (el.get() == config)
							return i;
						i++;
					}
				}
				throw new IllegalStateException("Element found but then not found");
			}
		}

		@Override
		public int getElementsAfter(ElementId id) {
			try (Transaction t = getConfig().lock(false, null)) {
				ObservableConfig config = CollectionElement.get(getConfig().theContent.getElement(id));
				if (config == null || !thePathElement.matches(config))
					throw new NoSuchElementException();
				int i = 0;
				for (CollectionElement<ObservableConfig> el : getConfig().theContent.reverse().elements()) {
					if (thePathElement.matches(el.get())) {
						if (el.get() == config)
							return i;
						i++;
					}
				}
				throw new IllegalStateException("Element found but then not found");
			}
		}

		@Override
		public CollectionElement<C> getElement(C value, boolean first) {
			if (!thePathElement.matches(value))
				return null;
			try (Transaction t = getConfig().lock(false, null)) {
				ObservableConfig config = CollectionElement.get(getConfig().theContent.getElement(value, first));
				return config == null ? null : new ConfigCollectionElement<>(config);
			}
		}

		@Override
		public CollectionElement<C> getElement(ElementId id) {
			try (Transaction t = getConfig().lock(false, null)) {
				ObservableConfig config = CollectionElement.get(getConfig().theContent.getElement(id));
				if (!thePathElement.matches(config))
					throw new NoSuchElementException();
				return new ConfigCollectionElement<>(config);
			}
		}

		@Override
		public CollectionElement<C> getTerminalElement(boolean first) {
			try (Transaction t = getConfig().lock(false, null)) {
				ObservableConfig config = CollectionElement.get(getConfig().theContent.getTerminalElement(first));
				while (config != null && !thePathElement.matches(config))
					config = CollectionElement.get(getConfig().theContent.getAdjacentElement(config.getParentChildRef(), first));
				return config == null ? null : new ConfigCollectionElement<>(config);
			}
		}

		@Override
		public CollectionElement<C> getAdjacentElement(ElementId elementId, boolean next) {
			try (Transaction t = getConfig().lock(false, null)) {
				ObservableConfig config = CollectionElement.get(getConfig().theContent.getAdjacentElement(elementId, next));
				while (config != null && !thePathElement.matches(config))
					config = CollectionElement.get(getConfig().theContent.getAdjacentElement(config.getParentChildRef(), next));
				return config == null ? null : new ConfigCollectionElement<>(config);
			}
		}

		@Override
		public MutableCollectionElement<C> mutableElement(ElementId id) {
			try (Transaction t = getConfig().lock(false, null)) {
				ObservableConfig config = CollectionElement.get(getConfig().theContent.getElement(id));
				if (!thePathElement.matches(config))
					throw new NoSuchElementException();
				return new MutableConfigCollectionElement<>(config, this);
			}
		}

		@Override
		public String canAdd(C value, ElementId after, ElementId before) {
			return StdMsg.UNSUPPORTED_OPERATION;
		}

		@Override
		public CollectionElement<C> addElement(C value, ElementId after, ElementId before, boolean first)
			throws UnsupportedOperationException, IllegalArgumentException {
			throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
		}

		@Override
		public void setValue(Collection<ElementId> elements, C value) {
			throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
		}

		@Override
		public void clear() {
			try (Transaction t = getConfig().lock(true, null)) {
				for (CollectionElement<ObservableConfig> el : getConfig().theContent.elements()) {
					if (thePathElement.matches(el.get()))
						el.get().remove();
				}
			}
		}

		@Override
		public Subscription onChange(Consumer<? super ObservableCollectionEvent<? extends C>> observer) {
			String watchPath = thePathElement.getAttributes().isEmpty() ? ANY_NAME : ANY_DEPTH;
			return getConfig().watch(getConfig().createPath(watchPath)).act(evt -> {
				if (evt.relativePath.size() > 2) // Too deep to affect path matching currently
					return;
				C child = (C) evt.relativePath.get(0);
				boolean postMatches = thePathElement.matches(child);
				boolean preMatches = evt.changeType == CollectionChangeType.set ? thePathElement.matchedBefore(child, evt.asFromChild())
					: postMatches;
				if (preMatches || postMatches) {
					int index;
					if (postMatches)
						index = getElementsBefore(child.getParentChildRef());
					else {
						int i = 0;
						for (CollectionElement<ObservableConfig> el : getConfig().theContent.elements()) {
							if (el.get() == child) {
								index = i;
								break;
							} else if (thePathElement.matches(el.get())) {
								i++;
							}
						}
						throw new IllegalStateException("Element found but then not found");
					}

					CollectionChangeType changeType;
					if (preMatches && postMatches)
						changeType = evt.changeType;
					else if (preMatches)
						changeType = CollectionChangeType.remove;
					else
						changeType = CollectionChangeType.add;

					C oldValue = changeType == CollectionChangeType.add ? null : child;
					ObservableCollectionEvent<C> collEvt = new ObservableCollectionEvent<>(child.getParentChildRef(), getType(), index,
						changeType, oldValue, child, evt);
					observer.accept(collEvt);
				}
			});
		}
	}
}
