package org.observe.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.io.Writer;
import java.text.ParseException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.Observer;
import org.observe.SettableValue;
import org.observe.Subscription;
import org.observe.collect.CollectionChangeType;
import org.observe.collect.ObservableCollection;
import org.observe.config.ObservableConfigContent.FullObservableConfigContent;
import org.observe.config.ObservableConfigContent.ObservableChildSet;
import org.observe.config.ObservableConfigContent.ObservableConfigChild;
import org.observe.config.ObservableConfigContent.ObservableConfigEntityValues;
import org.observe.config.ObservableConfigContent.ObservableConfigValue;
import org.observe.config.ObservableConfigContent.ObservableConfigValues;
import org.observe.config.ObservableConfigContent.SimpleObservableConfigContent;
import org.observe.util.TypeTokens;
import org.qommons.Causable;
import org.qommons.StringUtils;
import org.qommons.StructuredTransactable;
import org.qommons.Transaction;
import org.qommons.ValueHolder;
import org.qommons.collect.BetterList;
import org.qommons.collect.BetterSortedMap;
import org.qommons.collect.BetterSortedSet.SortedSearchFilter;
import org.qommons.collect.CollectionLockingStrategy;
import org.qommons.collect.ElementId;
import org.qommons.collect.ListenerList;
import org.qommons.collect.MapEntryHandle;
import org.qommons.collect.StampedLockingStrategy;
import org.qommons.io.Format;
import org.qommons.tree.BetterTreeList;
import org.qommons.tree.BetterTreeMap;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

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
	// TODO Need to uninstall listeners for removed descendants

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
			return str.toString();
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
			if (isUsed)
				throw new IllegalStateException("This builder has already been used");
			if (theAttributes == null)
				theAttributes = new LinkedHashMap<>();
			theAttributes.put(attrName, value);
			return this;
		}

		public ObservableConfigPathBuilder multi() {
			if (isUsed)
				throw new IllegalStateException("This builder has already been used");
			isMulti = true;
			return this;
		}

		protected void seal() {
			if (isUsed)
				throw new IllegalStateException("This builder has already been used");
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

	private ObservableConfig theParent;
	private ElementId theParentContentRef;
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
		if (name.length() == 0)
			throw new IllegalArgumentException("Name must not be empty");
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
		if (theParentContentRef == null || !theParentContentRef.isPresent())
			return null;
		else
			return theParent;
	}

	public String getPath() {
		if (theParent != null)
			return theParent.getPath() + PATH_SEPARATOR_STR + theName;
		else
			return theName;
	}

	public int getIndexInParent() {
		return theParent.theContent.getElementsBefore(theParentContentRef);
	}

	ElementId getParentChildRef() {
		return theParentContentRef;
	}

	public ObservableConfigPathBuilder buildPath(String firstName) {
		return buildPath(new LinkedList<>(), firstName);
	}

	protected static final Pattern PATH_EL_PATTERN = Pattern.compile("(?<name>.*)\\{\\s*(.+)=(.+)(>\\s*,(.+)=(.+))*\\s*\\}");

	protected ObservableConfigPathBuilder buildPath(List<ObservableConfigPathElement> path, String name) {
		List<ObservableConfigPathElement> finalPath = new ArrayList<>(path.size());
		finalPath.addAll(path);
		Map<String, String> properties = null;
		if (name.charAt(name.length() - 1) == '}') { // Quick check to avoid pattern checking on every single path, few of which will have
			// attrs
			Matcher matcher = PATH_EL_PATTERN.matcher(name);
			if (matcher.matches()) {
				properties = new LinkedHashMap<>();
				for (int g = 1; g < matcher.groupCount(); g++) {
					System.out.println("Group " + g + "=" + matcher.group(g));
				}
				name = matcher.group("name");
			}
		}
		ObservableConfigPathBuilder builder = new ObservableConfigPathBuilder(this, finalPath, name);
		if (properties != null) {
			for (Map.Entry<String, String> property : properties.entrySet()) {
				builder.withAttribute(property.getKey(), property.getValue());
			}
		}
		return builder;
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

	public ObservableValueSet<? extends ObservableConfig> getAllContent() {
		return getContent(ANY_NAME);
	}

	public ObservableValueSet<? extends ObservableConfig> getContent(String path) {
		return getContent(createPath(path));
	}

	public ObservableValueSet<? extends ObservableConfig> getContent(ObservableConfigPath path) {
		return getContent(path, Observable.empty());
	}

	public ObservableValueSet<? extends ObservableConfig> getContent(ObservableConfigPath path, Observable<?> until) {
		ObservableCollection<? extends ObservableConfig> children;
		if (path.getElements().size() == 1) {
			if (path.getLastElement().getName().equals(ANY_NAME))
				children = new FullObservableConfigContent<>(this, getType());
			else
				children = new SimpleObservableConfigContent<>(this, TYPE, path.getLastElement());
		} else {
			ObservableConfigPath last = path.getLast();
			TypeToken<ObservableConfig> type = (TypeToken<ObservableConfig>) getType();
			TypeToken<ObservableCollection<ObservableConfig>> collType = ObservableCollection.TYPE_KEY.getCompoundType(type);
			children = ObservableCollection.flattenValue(observeDescendant(path.getParent()).map(collType,
				p -> (ObservableCollection<ObservableConfig>) p.getContent(last, until).getValues(), //
				opts -> opts.cache(true).reEvalOnUpdate(false).fireIfUnchanged(false)));
		}
		return new ObservableChildSet<>(this, path, children, until);
	}

	public ObservableValue<? extends ObservableConfig> observeDescendant(ObservableConfigPath path) {
		return new ObservableConfigChild<>(getType(), this, path);
	}

	public SettableValue<String> observeValue() {
		return observeValue(TypeTokens.get().STRING, Format.TEXT);
	}

	public String get(String path) {
		return get(createPath(path));
	}

	public String get(ObservableConfigPath path) {
		ObservableConfig config = getChild(path, false, null);
		return config == null ? null : config.getValue();
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
		return new ObservableConfigValue<>(type, this, path, parser, format);
	}

	public <T> ObservableCollection<T> observeValues(ObservableConfigPath path, TypeToken<T> type, Format<T> format) {
		return observeValues(path, type, config -> {
			if (config.getValue() == null)
				return null;
			try {
				return format.parse(config.getValue());
			} catch (ParseException e) {
				System.err.println("Could not parse value " + this + ": " + e.getMessage());
				return null;
			}
		}, (config, val) -> config.setValue(format.format(val)), true, Observable.empty());
	}

	public <T> ObservableCollection<T> observeValues(ObservableConfigPath path, TypeToken<T> type,
		Function<ObservableConfig, ? extends T> parser, BiConsumer<ObservableConfig, ? super T> format, boolean passive,
		Observable<?> until) {
		ObservableChildSet<? extends ObservableConfig> configs = (ObservableChildSet<? extends ObservableConfig>) getContent(path, until);
		return new ObservableConfigValues<>(configs, type, parser, format, passive, until);
	}

	public <T> ObservableValueSet<T> observeEntities(ObservableConfigPath path, TypeToken<T> type, Observable<?> until) {
		return observeEntities(path, type, new ConfigEntityFieldParser(), until);
	}

	public <T> ObservableValueSet<T> observeEntities(ObservableConfigPath path, TypeToken<T> type, ConfigEntityFieldParser fieldParser,
		Observable<?> until) {
		ObservableValueSet<? extends ObservableConfig> configs = getContent(path, until);
		return new ObservableConfigEntityValues<>(configs, type, fieldParser, until);
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

	public ObservableConfig getChild(String path, boolean createIfAbsent, Consumer<ObservableConfig> preAddMod) {
		return getChild(createPath(path), createIfAbsent, preAddMod);
	}

	public ObservableConfig getChild(ObservableConfigPath path, boolean createIfAbsent, Consumer<ObservableConfig> preAddMod) {
		try (Transaction t = lock(createIfAbsent, null)) {
			ObservableConfig ret = this;
			for (ObservableConfigPathElement el : path.getElements()) {
				ObservableConfig found = ret.getChild(el, createIfAbsent, preAddMod);
				if (found == null)
					return null;
				ret = found;
			}
			return ret;
		}
	}

	protected ObservableConfig getChild(ObservableConfigPathElement el, boolean createIfAbsent, Consumer<ObservableConfig> preAddMod) {
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
				if (preAddMod != null)
					preAddMod.accept(ch);
			});
		}
		return found;
	}

	public ObservableConfig addChild(String name) {
		return addChild(name, null);
	}

	public ObservableConfig addChild(String name, Consumer<ObservableConfig> preAddMod) {
		return addChild(null, null, false, name, preAddMod);
	}

	public ObservableConfig addChild(ObservableConfig after, ObservableConfig before, boolean first, String name,
		Consumer<ObservableConfig> preAddMod) {
		try (Transaction t = lock(true, true, null)) {
			ElementId el = theContent.addElement(null, //
				after == null ? null : Objects.requireNonNull(after.theParentContentRef),
					before == null ? null : Objects.requireNonNull(before.theParentContentRef), //
						first).getElementId();
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
		else if (name.length() == 0)
			throw new IllegalArgumentException("Name must not be empty");
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
		ObservableConfig child = getChild(path, true, ch -> ch.setValue(value));
		if (child.getValue() != value)
			child.setValue(value);
		return this;
	}

	public void remove() {
		try (Transaction t = lock(true, null)) {
			if (!theParentContentRef.isPresent())
				return;
			theParent.theContent.mutableElement(theParentContentRef).remove();
			fire(CollectionChangeType.remove, Collections.emptyList(), theName, theValue);
		}
		theParent = null;
		theParentContentRef = null;
	}

	@Override
	public String toString() {
		StringWriter out = new StringWriter();
		try {
			_writeXml(this, out, new XmlEncoding(":", ":", ""), 0, "", new XmlWriteHelper(), false);
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
		return out.toString();
	}

	protected final Object getCurrentCause() {
		return theRootCausable.get();
	}

	protected void fire(CollectionChangeType eventType, List<ObservableConfig> relativePath, String oldName, String oldValue) {
		_fire(eventType, relativePath, oldName, oldValue, getCurrentCause());
	}

	private void _fire(CollectionChangeType eventType, List<ObservableConfig> relativePath, String oldName, String oldValue, Object cause) {
		theModCount++;
		if (eventType != CollectionChangeType.set)
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
		boolean fireWithParent;
		if (theParent == null)
			fireWithParent = false;
		else if (theParentContentRef.isPresent())
			fireWithParent = true;
		else
			fireWithParent = eventType == CollectionChangeType.remove && relativePath.isEmpty(); // Means this config was just removed
		if (fireWithParent)
			theParent.fire(eventType, addToList(this, relativePath), oldName, oldValue);
	}

	private static List<ObservableConfig> addToList(ObservableConfig c, List<ObservableConfig> list) {
		ObservableConfig[] array = new ObservableConfig[list.size() + 1];
		array[0] = c;
		for (int i = 0; i < list.size(); i++)
			array[i + 1] = list.get(i);
		return Arrays.asList(array);
	}

	/** Needed by ObservableConfigContent.* */
	BetterList<ObservableConfig> _getContent() {
		return theContent;
	}

	/** Needed by ObservableConfigContent.* */
	ElementId _getParentContentRef() {
		return theParentContentRef;
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

	public static ObservableConfig createRoot(String name) {
		return createRoot(name, null, new StampedLockingStrategy());
	}

	public static ObservableConfig createRoot(String name, String value, CollectionLockingStrategy locking) {
		return new ObservableConfig(null, null, name, locking, new ValueHolder<>(), value);
	}

	public static class XmlEncoding {
		public final String encodingPrefix;
		public final String encodingReplacement;
		public final String emptyContent;
		public final BetterSortedMap<String, String> namedReplacements;
		private final StringBuilder str;

		public XmlEncoding(String encodingPrefix, String encodingReplacement, String emptyContent, Map<String, String> replacements) {
			this.encodingPrefix = encodingPrefix;
			this.encodingReplacement = encodingReplacement;
			this.emptyContent = emptyContent;
			if (replacements instanceof BetterSortedMap)
				namedReplacements = (BetterSortedMap<String, String>) replacements;
			else
				namedReplacements = new BetterTreeMap<String, String>(false, String::compareTo).withAll(replacements);
			str = new StringBuilder();
		}

		public XmlEncoding(String encodingPrefix, String encodingReplacement, String emptyContent, String... replacements) {
			this(encodingPrefix, encodingReplacement, emptyContent, mapify(replacements));
		}

		private static Map<String, String> mapify(String[] replacements) {
			if (replacements.length == 0)
				return Collections.emptyMap();
			if (replacements.length % 2 != 0)
				throw new IllegalArgumentException("Replacements must be key, value, key, value...");
			Map<String, String> map = new LinkedHashMap<>(replacements.length * 3 / 4);
			for (int i = 0; i < replacements.length; i += 2) {
				map.put(replacements[i], replacements[i + 1]);
			}
			return Collections.unmodifiableMap(map);
		}

		public String encode(String xmlName) {
			MapEntryHandle<String, String> found = null;
			int i;
			for (i = 0; i < xmlName.length() && found == null; i++) {
				int offset = i;
				found = namedReplacements.search(r -> searchMatch(xmlName, r, offset), SortedSearchFilter.OnlyMatch);
			}
			if (found == null)
				return xmlName;

			str.append(xmlName, 0, i);
			do {
				str.append(encodingPrefix);
				str.append(found.getValue());
				i += found.getKey().length();
				found = null;
				for (; i < xmlName.length() && found == null; i++) {
					int offset = i;
					found = namedReplacements.search(r -> searchMatch(xmlName, r, offset), SortedSearchFilter.OnlyMatch);
				}
			} while (found != null);
			str.append(xmlName, i, xmlName.length());
			String encoded = str.toString();
			str.setLength(0);
			return encoded;
		}

		public String decode(String xmlName) {
			int len = xmlName.length() - Math.min(encodingPrefix.length(), encodingReplacement.length());
			int c;
			for (c = 0; c < len; c++) {
				if (matches(xmlName, encodingReplacement, c)//
					|| matches(xmlName, encodingPrefix, c))
					break;
			}
			if (c == len)
				return xmlName;

			str.append(xmlName, 0, c);
			for (; c < len; c++) {
				if (matches(xmlName, encodingReplacement, c)) {
					str.append(encodingPrefix);
					c += encodingReplacement.length() - 1;
				} else if (matches(xmlName, encodingPrefix, c)) {
					c += encodingPrefix.length();
					boolean found = false;
					for (Map.Entry<String, String> replacement : namedReplacements.entrySet()) {
						if (matches(xmlName, replacement.getValue(), c)) {
							str.append(replacement.getKey());
							c += replacement.getValue().length() - 1;
							found = true;
							break;
						}
					}
					if (!found)
						str.append(encodingPrefix);
				} else {
					str.append(xmlName.charAt(c));
				}
			}
			str.append(xmlName, c, xmlName.length());
			String decoded = str.toString();
			str.setLength(0);
			return decoded;
		}

		private static boolean matches(String xmlName, String encoding, int xmlOffset) {
			if (xmlName.length() < xmlOffset + encoding.length())
				return false;
			for (int c = 0; c < encoding.length(); c++)
				if (xmlName.charAt(xmlOffset + c) != encoding.charAt(c))
					return false;
			return true;
		}

		private static int searchMatch(String xmlName, String replace, int xmlOffset) {
			int limit = Math.min(replace.length(), xmlName.length() - xmlOffset);
			int c;
			for (c = 0; c < limit; c++) {
				int diff = replace.charAt(c) - xmlName.charAt(xmlOffset + c);
				if (diff != 0)
					return diff;
			}
			if (c < replace.length())
				return 1;
			return 0;
		}
	}

	/**
	 * Populates an ObservableConfig from an XML stream
	 *
	 * @param config The config to populate. If the config is not initially empty, content in the form of attributes or elements will be
	 *        appended, the value of the config will be replaced.
	 * @param in The input stream containing the XML data
	 * @param encoding The scheme to use for decoding illegal XML names from their serialized forms
	 * @throws IOException If an error occurs reading the document
	 * @throws SAXException If an error occurs parsing the document
	 */
	public static void readXml(ObservableConfig config, InputStream in, XmlEncoding encoding) throws IOException, SAXException {
		SAXParserFactory factory = SAXParserFactory.newInstance();
		SAXParser parser;
		try {
			parser = factory.newSAXParser();
		} catch (ParserConfigurationException e) {
			throw new IllegalStateException(e);
		}

		try (Transaction t = config.lock(true, null)) {
			parser.parse(in, new DefaultHandler() {
				private boolean isRoot = true;
				private final LinkedList<ObservableConfig> theStack = new LinkedList<>();
				private final ArrayList<StringBuilder> theContentStack = new ArrayList<>();
				private boolean hasContent;
				private final StringBuilder theIgnorableContent = new StringBuilder();

				@Override
				public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
					theIgnorableContent.setLength(0);
					hasContent = false;

					ObservableConfig newConfig;
					String name = encoding.decode((localName != null && localName.length() > 0) ? localName : qName);
					if (isRoot) {
						newConfig = config;
						config.setName(name);
						persistAttributes(config, attributes);
						isRoot = false;
					} else {
						newConfig = theStack.getLast().addChild(encoding.decode(name), newCfg -> {
							persistAttributes(newCfg, attributes);
						});
					}
					theStack.add(newConfig);
					if (theContentStack.size() < theStack.size())
						theContentStack.add(new StringBuilder());
				}

				private void persistAttributes(ObservableConfig cfg, Attributes attributes) {
					for (int a = 0; a < attributes.getLength(); a++)
						cfg.set(encoding.decode(attributes.getLocalName(a)), attributes.getValue(a));
				}

				@Override
				public void characters(char[] ch, int start, int length) throws SAXException {
					StringBuilder content = theContentStack.get(theStack.size() - 1);
					for (int i = 0; i < length; i++) {
						char c = ch[start + i];
						if (Character.isWhitespace(c)) {
							if (!hasContent)
								theIgnorableContent.append(c);
						} else {
							if (theIgnorableContent.length() > 0) {
								if (content.length() > 0)
									content.append(theIgnorableContent);
								theIgnorableContent.setLength(0);
							}
							content.append(c);
						}
					}
				}

				@Override
				public void ignorableWhitespace(char[] ch, int start, int length) throws SAXException {
					// It seems this doesn't actually ever happen in practice, it just goes to the characters() method
					// But this is what should happen if it is called
					if (!hasContent)
						theIgnorableContent.append(ch, start, length);
				}

				@Override
				public void endElement(String uri, String localName, String qName) throws SAXException {
					ObservableConfig cfg = theStack.removeLast();
					StringBuilder content = theContentStack.get(theStack.size());
					String contentStr;
					if (!hasContent && content.length() == 0) { // If there's no content but there is whitespace, use it as the content
						if (theIgnorableContent.length() > 0) {
							contentStr = theIgnorableContent.toString();
							theIgnorableContent.setLength(0);
							if (contentStr.equals(encoding.emptyContent))
								contentStr = ""; // No way to distinguish <el></el> from <el /> so this is the best we can do
						} else
							contentStr = null;
					} else { // If there's actual content, ignore the whitespace
						if (content.length() > 0) {
							contentStr = content.toString();
							content.setLength(0);
						} else
							contentStr = null;
						theIgnorableContent.setLength(0);
					}
					if (!Objects.equals(contentStr, cfg.getValue()))
						cfg.setValue(contentStr);
					hasContent = true;
				}
			});
		}
	}

	public static void writeXml(ObservableConfig config, Writer out, XmlEncoding encoding, String indent) throws IOException {
		out.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n\n");
		try (Transaction t = config.lock(false, null)) {
			_writeXml(config, out, encoding, 0, indent, new XmlWriteHelper(), true);
		}
		out.append('\n');
	}

	private static class XmlWriteHelper {
		final Map<String, Integer> attrNames = new HashMap<>();
		final BitSet childrenAsAttributes = new BitSet();
		final StringBuilder escapeTemp = new StringBuilder();

		String escapeXml(String xmlValue, XmlEncoding encoding) {
			if (xmlValue.length() == 0)
				return encoding.emptyContent;
			int c;
			for (c = 0; c < xmlValue.length(); c++) {
				char ch = xmlValue.charAt(c);
				if (ch == '<')
					break;
				else if (ch == '&')
					break;
				else if (ch == 0)
					throw new IllegalArgumentException("Cannot encode NUL");
				else if (ch < '\t' || ch > '~')
					break;
			}
			if (c == xmlValue.length())
				return xmlValue;
			escapeTemp.append(xmlValue, 0, c);
			for (; c < xmlValue.length(); c++) {
				char ch = xmlValue.charAt(c);
				if (ch == '<')
					escapeTemp.append("&lt;");
				else if (ch == '&')
					escapeTemp.append("&amp;");
				else if (ch == 0)
					throw new IllegalArgumentException("Cannot encode NUL");
				else if (ch < '\t' || ch > '~') {
					escapeTemp.append("&#x");
					escapeTemp.append(Integer.toHexString(ch));
					escapeTemp.append(';');
				} else
					escapeTemp.append(ch);
			}
			String escaped = escapeTemp.toString();
			escapeTemp.setLength(0);
			return escaped;
		}
	}

	private static void _writeXml(ObservableConfig config, Writer out, XmlEncoding encoding, int indentAmount, String indentStr,
		XmlWriteHelper helper, boolean withChildren) throws IOException {
		for (int i = 0; i < indentAmount; i++)
			out.append(indentStr);
		String xmlName = encoding.encode(config.getName());
		out.append('<').append(xmlName);

		// See if any of the children should be attributes
		String singular = StringUtils.singularize(config.getName());
		int i = 0;
		for (ObservableConfig child : config.theContent) {
			boolean maybeAttr = child.mayBeAttribute();
			if (maybeAttr && child.getName().equals(singular))
				maybeAttr = false;
			if (maybeAttr) {
				Integer old = helper.attrNames.put(child.getName(), i);
				if (old == null)
					helper.childrenAsAttributes.set(i);
				else
					helper.childrenAsAttributes.clear(old);
			}
			i++;
		}
		helper.attrNames.clear();
		if (!helper.childrenAsAttributes.isEmpty()) {
			for (i = helper.childrenAsAttributes.nextSetBit(0); i >= 0; i = helper.childrenAsAttributes.nextSetBit(i + 1)) {
				ObservableConfig child = config.theContent.get(i);
				out.append(' ').append(encoding.encode(child.getName())).append("=\"").append(helper.escapeXml(child.getValue(), encoding))
				.append('"');
			}
		}
		if (withChildren)
			withChildren = helper.childrenAsAttributes.cardinality() < config.theContent.size();
		if (!withChildren && config.theValue == null) {
			out.append(" />");
			helper.childrenAsAttributes.clear();
		} else {
			out.append(">");
			if (config.theValue != null)
				out.append(helper.escapeXml(config.getValue(), encoding));
			if (withChildren) {
				i = 0;
				BitSet copy = (BitSet) helper.childrenAsAttributes.clone();
				helper.childrenAsAttributes.clear();
				for (ObservableConfig child : config.theContent) {
					if (copy.get(i))
						continue;
					out.append('\n');
					_writeXml(child, out, encoding, indentAmount + 1, indentStr, helper, true);
					i++;
				}

				out.append('\n');
				for (i = 0; i < indentAmount; i++)
					out.append(indentStr);
			}
			out.append("</").append(xmlName).append('>');
		}
	}

	protected boolean mayBeAttribute() {
		if (theValue == null || theValue.length() == 0 || !theContent.isEmpty())
			return false;
		for (int c = 0; c < theValue.length(); c++)
			if (theValue.charAt(c) < ' ' || theValue.charAt(c) > '~')
				return false;
		return true;
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
		int lastSep = -1;
		while (pathSepIdx >= 0) {
			pathEls.add(path.substring(lastSep + 1, pathSepIdx));
			lastSep = pathSepIdx;
			pathSepIdx = path.indexOf(PATH_SEPARATOR, pathSepIdx + 1);
		}
		pathEls.add(path.substring(lastSep + 1));
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

}
