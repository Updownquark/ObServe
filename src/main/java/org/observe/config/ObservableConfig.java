package org.observe.config;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.io.Writer;
import java.text.ParseException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.Observer;
import org.observe.SettableValue;
import org.observe.SimpleObservable;
import org.observe.Subscription;
import org.observe.assoc.ObservableMap;
import org.observe.assoc.ObservableMultiMap;
import org.observe.collect.CollectionChangeType;
import org.observe.collect.ObservableCollection;
import org.observe.config.ObservableConfigContent.FullObservableConfigContent;
import org.observe.config.ObservableConfigContent.ObservableChildSet;
import org.observe.config.ObservableConfigContent.ObservableConfigChild;
import org.observe.config.ObservableConfigContent.SimpleObservableConfigContent;
import org.observe.config.ObservableConfigPath.ObservableConfigPathElement;
import org.observe.util.TypeTokens;
import org.qommons.Causable;
import org.qommons.Nameable;
import org.qommons.Stamped;
import org.qommons.StringUtils;
import org.qommons.Transactable;
import org.qommons.Transaction;
import org.qommons.collect.BetterList;
import org.qommons.collect.BetterSortedList;
import org.qommons.collect.BetterSortedMap;
import org.qommons.collect.CollectionLockingStrategy;
import org.qommons.collect.ElementId;
import org.qommons.collect.MapEntryHandle;
import org.qommons.ex.ExFunction;
import org.qommons.io.Format;
import org.qommons.tree.BetterTreeMap;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import com.google.common.reflect.TypeToken;

/**
 * <p>
 * A hierarchical structure of configuration elements. Each element has a name, a value (string), and any number of child configuration
 * elements. It is intended to provide an easy injection route for configuration data files (e.g. XML) into an application. Utilities are
 * provided to serialize and deserialize to persistence on various triggers, as well as to convert hierarchical text configuration into
 * simple objects, collections, and entities--all linked back to the same data source and updated with it.
 * </p>
 * <p>
 * The structure fires detailed events when it is changed, and utilities are provided to monitor individual configuration values and
 * configurable collections of child configurations as standard observable structures.
 * </p>
 */
public interface ObservableConfig extends Nameable, Transactable, Stamped {
	public static final TypeToken<ObservableConfig> TYPE = TypeTokens.get().of(ObservableConfig.class);

	public static class ObservableConfigEvent extends Causable.AbstractCausable {
		public final CollectionChangeType changeType;
		public final boolean isMove;
		public final ObservableConfig eventTarget;
		public final BetterList<ObservableConfig> relativePath;
		public final String oldName;
		public final String oldValue;

		public ObservableConfigEvent(CollectionChangeType changeType, boolean move, ObservableConfig eventTarget, String oldName,
			String oldValue, BetterList<ObservableConfig> relativePath, Object cause) {
			super(cause);
			this.changeType = changeType;
			this.isMove = move;
			this.eventTarget = eventTarget;
			this.relativePath = relativePath;
			this.oldName = oldName;
			this.oldValue = oldValue;
		}

		public String getRelativePathString() {
			StringBuilder str = new StringBuilder();
			for (ObservableConfig p : relativePath) {
				if (str.length() > 0)
					str.append(ObservableConfigPath.PATH_SEPARATOR);
				str.append(p.getName());
			}
			return str.toString();
		}

		public ObservableConfigEvent asFromChild() {
			return new ObservableConfigEvent(changeType, isMove, relativePath.get(0), oldName, oldValue,
				relativePath.subList(1, relativePath.size()), getCauses());
		}

		@Override
		public String toString() {
			StringBuilder str = new StringBuilder(eventTarget.getName());
			switch (changeType) {
			case add:
				str.append(":+").append(relativePath.isEmpty() ? "this" : getRelativePathString());
				break;
			case remove:
				str.append(":-").append(relativePath.isEmpty() ? "this" : getRelativePathString());
				break;
			case set:
				str.append(ObservableConfigPath.PATH_SEPARATOR).append(relativePath.isEmpty() ? "this" : getRelativePathString());
				ObservableConfig changed = relativePath.isEmpty() ? eventTarget : relativePath.get(relativePath.size() - 1);
				if (!oldName.equals(changed.getName()))
					str.append(".name ").append(oldName).append("->").append(changed.getName());
				else
					str.append(".value ").append(oldValue).append("->").append(changed.getValue());
			}
			return str.toString();
		}
	}

	public static interface ObservableConfigPersistence<E extends Exception> {
		void persist(ObservableConfig config) throws E;
	}

	Object getParsedItem(ObservableConfigParseSession session);

	ObservableConfig withParsedItem(ObservableConfigParseSession session, Object item);

	String getValue();

	ObservableConfig getParent();

	default String getPath() {
		ObservableConfig parent = getParent();
		if (parent != null)
			return parent.getPath() + ObservableConfigPath.PATH_SEPARATOR_STR + getName();
		else
			return getName();
	}

	ElementId getParentChildRef();

	default int getIndexInParent() {
		ElementId pcr = getParentChildRef();
		return pcr == null ? null : getParent().getAllContent().getValues().getElementsBefore(pcr);
	}

	default Observable<ObservableConfigEvent> watch(String pathFilter) {
		return watch(ObservableConfigPath.create(pathFilter));
	}

	Observable<ObservableConfigEvent> watch(ObservableConfigPath path);

	default ObservableCollection<ObservableConfig> getContent() {
		return new FullObservableConfigContent(this);
	}

	default SyncValueSet<? extends ObservableConfig> getAllContent() {
		return getContent(ObservableConfigPath.ANY_NAME);
	}

	default SyncValueSet<? extends ObservableConfig> getContent(String path) {
		return getContent(ObservableConfigPath.create(path));
	}

	default SyncValueSet<? extends ObservableConfig> getContent(ObservableConfigPath path) {
		ObservableCollection<ObservableConfig> children;
		if (path.getElements().size() == 1) {
			if (path.getLastElement().isMulti())
				children = new FullObservableConfigContent(this);
			else
				children = new SimpleObservableConfigContent(this, path.getLastElement());
		} else {
			ObservableConfigPath last = path.getLast();
			TypeToken<ObservableCollection<ObservableConfig>> collType = TypeTokens.get().keyFor(ObservableCollection.class)
				.parameterized(TYPE);
			ObservableValue<? extends ObservableConfig> descendant = observeDescendant(path.getParent());
			ObservableCollection<ObservableConfig> emptyChildren = ObservableCollection.of(TYPE);
			children = ObservableCollection.flattenValue(descendant.map(collType,
				p -> (ObservableCollection<ObservableConfig>) (p == null ? emptyChildren : p.getContent(last).getValues()), //
				opts -> opts.cache(true).reEvalOnUpdate(false).fireIfUnchanged(false)));
		}
		return new ObservableChildSet(this, path, children);
	}

	default ObservableValue<ObservableConfig> observeDescendant(String path) {
		return observeDescendant(ObservableConfigPath.create(path));
	}

	default ObservableValue<ObservableConfig> observeDescendant(ObservableConfigPath path) {
		return new ObservableConfigChild(this, path);
	}

	default SettableValue<String> observeValue() {
		return observeValue(ObservableConfigPath.EMPTY_PATH);
	}

	default SettableValue<String> observeValue(String path) {
		return observeValue(ObservableConfigPath.create(path));
	}

	default SettableValue<String> observeValue(ObservableConfigPath path) {
		return new ObservableConfigContent.ObservableConfigValue(this, path);
	}

	default String get(String path) {
		return get(ObservableConfigPath.create(path));
	}

	default String get(ObservableConfigPath path) {
		ObservableConfig config = getChild(path, false, null);
		return config == null ? null : config.getValue();
	}

	/**
	 * @param type The type of the value or element to produce
	 * @return A builder with many options that allows the construction of a formatted value, collection, or entity set
	 */
	default <T> ObservableConfigValueBuilder<T> asValue(Class<T> type) {
		return asValue(TypeTokens.get().of(type));
	}

	/**
	 * @param type The type of the value or element to produce
	 * @return A builder with many options that allows the construction of a formatted value, collection, or entity set
	 */
	default <T> ObservableConfigValueBuilder<T> asValue(TypeToken<T> type) {
		return new ObservableConfigValueBuilder<>(this, type);
	}

	/**
	 * Formats ObservableConfig data into any number of different types of data structures
	 *
	 * @param <T> The value type of the structure to create
	 */
	public class ObservableConfigValueBuilder<T> {
		private final ObservableConfig theConfig;
		private final TypeToken<T> theType;
		private ObservableConfigFormat<T> theFormat;
		private ObservableConfigFormatSet theFormatSet;
		private Observable<?> theUntil;
		private ObservableConfigPath thePath;
		private ObservableConfigParseSession theSession;
		private Observable<?> theBuiltNotifier;

		ObservableConfigValueBuilder(ObservableConfig config, TypeToken<T> type) {
			theConfig = config;
			theType = type;
		}

		public ObservableConfig getConfig() {
			return theConfig;
		}

		public ObservableConfigValueBuilder<T> withFormat(ObservableConfigFormat<T> format) {
			theFormat = format;
			return this;
		}

		public ObservableConfigValueBuilder<T> withFormat(Format<T> format, Supplier<? extends T> defaultValue) {
			theFormat = ObservableConfigFormat.ofQommonFormat(format, defaultValue);
			return this;
		}

		public ObservableConfigValueBuilder<T> withFormatSet(ObservableConfigFormatSet formatSet) {
			theFormatSet = formatSet;
			return this;
		}

		public ObservableConfigValueBuilder<T> withHierarchicalFormat(
			Function<ObservableConfigFormat.HeterogeneousFormat.Builder<T>, ObservableConfigFormat.HeterogeneousFormat<T>> format) {
			theFormat = format.apply(ObservableConfigFormat.heterogeneous(theType));
			return this;
		}

		public ObservableConfigValueBuilder<T> asEntity(Consumer<ObservableConfigFormat.EntityFormatBuilder<T>> format) {
			theFormat = getFormatSet().buildEntityFormat(theType, format);
			return this;
		}

		public ObservableConfigValueBuilder<T> at(String path) {
			if (path.length() == 0)
				return this;
			return at(ObservableConfigPath.create(path));
		}

		public ObservableConfigValueBuilder<T> at(ObservableConfigPath path) {
			thePath = path;
			return this;
		}

		public ObservableConfigValueBuilder<T> until(Observable<?> until) {
			theUntil = until;
			return this;
		}

		public ObservableConfigValueBuilder<T> withSession(ObservableConfigParseSession session) {
			theSession = session;
			return this;
		}

		/**
		 * @param built An observable that must fire after this and all related structures have been built. This will signal reference
		 *        formats to retrieve their values
		 * @return This builder
		 */
		public ObservableConfigValueBuilder<T> withBuiltNotifier(Observable<?> built) {
			theBuiltNotifier = built;
			return this;
		}

		public ObservableConfigParseSession getSession() {
			if (theSession == null)
				theSession = new ObservableConfigParseSession();
			return theSession;
		}

		protected ObservableConfigFormatSet getFormatSet() {
			if (theFormatSet == null)
				theFormatSet = new ObservableConfigFormatSet();
			return theFormatSet;
		}

		protected ObservableConfigFormat<T> getFormat() {
			ObservableConfigFormat<T> format = theFormat;
			if (format == null)
				format = getFormatSet().getConfigFormat(theType, theConfig.getName());
			return format;
		}

		protected ObservableValue<? extends ObservableConfig> getDescendant(boolean parent) {
			ObservableValue<? extends ObservableConfig> descendant;
			if (thePath != null) {
				if (parent) {
					if (thePath.getElements().size() == 1)
						descendant = ObservableValue.of(theConfig);
					else
						descendant = theConfig.observeDescendant(thePath.getParent());
				} else
					descendant = theConfig.observeDescendant(thePath);
			} else
				descendant = ObservableValue.of(theConfig);
			return descendant;
		}

		protected Supplier<ObservableConfig> createDescendant(boolean parent) {
			ObservableConfigPath path = parent ? thePath.getParent() : thePath;
			return () -> theConfig.getChild(path, true, null);
		}

		protected String getChildName() {
			if (thePath == null)
				return StringUtils.singularize(theConfig.getName());
			else
				return thePath.getLastElement().getName();
		}

		protected Observable<?> getUntil() {
			return theUntil == null ? Observable.empty() : theUntil;
		}

		protected ObservableConfigFormat.ObservableConfigParseContext<T> getParseContext(Observable<?> until, Observable<?> findRefs) {
			return ObservableConfigFormat.ctxFor(getSession(), theConfig, getDescendant(false),
				createDescendant(false)::get, null, until, null, findRefs, null);
		}

		protected <T, E extends Exception> T build(ExFunction<Observable<?>, T, E> build, Consumer<T> preRefs) throws E {
			Observable<?> builtNotifier = theBuiltNotifier;
			SimpleObservable<Void> findRefs;
			if (builtNotifier == null) {
				builtNotifier = findRefs = new SimpleObservable<>();
			} else
				findRefs = null;
			T built = build.apply(builtNotifier);
			if (preRefs != null)
				preRefs.accept(built);
			if (findRefs != null)
				findRefs.onNext(null);
			return built;
		}

		public T parse(Consumer<T> preReturnGet) throws ParseException {
			ObservableConfigFormat<T> format = getFormat();
			// If the format is simple, we can just parse the value and then forget about it.
			// Otherwise, we need to maintain the connection to update the value when configuration changes
			if (format instanceof ObservableConfigFormat.SimpleConfigFormat)
				return build(findRefs -> format.parse(getParseContext(getUntil(), findRefs)), preReturnGet);
			else {
				T built = buildValue(null).get();
				if (preReturnGet != null)
					preReturnGet.accept(built);
				return built;
			}
		}

		public T parseDisconnected() throws ParseException {
			SimpleObservable<Void> until = new SimpleObservable<>();
			SimpleObservable<Void> findRefs = new SimpleObservable<>();
			T value = getFormat().parse(getParseContext(until, findRefs));
			findRefs.onCompleted(null);
			until.onNext(null);
			return value;
		}

		public SettableValue<T> buildValue(Consumer<SettableValue<T>> preReturnGet) {
			return build(
				findRefs -> new ObservableConfigTransform.ObservableConfigValue<>(getSession(), theConfig,
					getDescendant(false), createDescendant(false)::get, getUntil(), theType, getFormat(), true, findRefs),
				preReturnGet);
		}

		public ObservableCollection<T> buildCollection(Consumer<ObservableCollection<T>> preReturnGet) {
			return build(findRefs -> new ObservableConfigTransform.ObservableConfigValues<>(getSession(), theConfig, //
				getDescendant(thePath != null), createDescendant(thePath != null)::get, theType, getFormat(), getChildName(), getUntil(),
				true, findRefs), preReturnGet);
		}

		public SyncValueSet<T> buildEntitySet(Consumer<SyncValueSet<T>> preReturnGet) {
			ObservableConfigFormat<T> entityFormat = getFormat();
			if (!(entityFormat instanceof ObservableConfigFormat.EntityConfigFormat))
				throw new IllegalStateException("Format for " + theType + " is not entity-enabled");
			return build(findRefs -> new ObservableConfigTransform.ObservableConfigEntityValues<>(getSession(), theConfig, //
				getDescendant(thePath != null), createDescendant(thePath != null)::get,
				(ObservableConfigFormat.EntityConfigFormat<T>) entityFormat, getChildName(), getUntil(), true, findRefs), preReturnGet);
		}

		public <K> ObservableConfigMapBuilder<K, T> asMap(TypeToken<K> keyType) {
			return new ObservableConfigMapBuilder<>(this, keyType);
		}
	}

	public class ObservableConfigMapBuilder<K, V> {
		private final ObservableConfigValueBuilder<V> theValueBuilder;
		private final TypeToken<K> theKeyType;
		private String theKeyName;
		private ObservableConfigFormat<K> theKeyFormat;

		ObservableConfigMapBuilder(ObservableConfigValueBuilder<V> valueBuilder, TypeToken<K> keyType) {
			theValueBuilder = valueBuilder;
			theKeyType = keyType;
		}

		public ObservableConfigMapBuilder<K, V> withKeyFormat(ObservableConfigFormat<K> format) {
			theKeyFormat = checkFormat(format);
			return this;
		}

		public ObservableConfigMapBuilder<K, V> withKeyFormat(Format<K> format, Supplier<? extends K> defaultValue) {
			theKeyFormat = ObservableConfigFormat.ofQommonFormat(format, defaultValue);
			return this;
		}

		public ObservableConfigMapBuilder<K, V> withFormatSet(ObservableConfigFormatSet formatSet) {
			theValueBuilder.withFormatSet(formatSet);
			return this;
		}

		public ObservableConfigMapBuilder<K, V> withHierarchicalFormat(
			Function<ObservableConfigFormat.HeterogeneousFormat.Builder<K>, ObservableConfigFormat.HeterogeneousFormat<K>> format) {
			theKeyFormat = checkFormat(format.apply(ObservableConfigFormat.heterogeneous(theKeyType)));
			return this;
		}

		private <K2 extends K> ObservableConfigFormat<K2> checkFormat(ObservableConfigFormat<K2> format) {
			if (format == null || format instanceof ObservableConfigFormat.SimpleConfigFormat)
				return format;
			else if (format instanceof ObservableConfigFormat.HeterogeneousFormat) {
				for (ObservableConfigFormat.HeterogeneousFormat.SubFormat<? extends K> subFormat//
					: ((ObservableConfigFormat.HeterogeneousFormat<K>) format).getSubFormats()) {
					checkFormat(subFormat.getFormat());
				}
			} else
				throw new IllegalArgumentException("Only simple key formats are supported");
			return format;
		}

		public ObservableConfigMapBuilder<K, V> at(String path) {
			theValueBuilder.at(path);
			return this;
		}

		public ObservableConfigMapBuilder<K, V> at(ObservableConfigPath path) {
			theValueBuilder.at(path);
			return this;
		}

		public ObservableConfigMapBuilder<K, V> until(Observable<?> until) {
			theValueBuilder.until(until);
			return this;
		}

		public ObservableConfigMapBuilder<K, V> withKeyName(String keyName) {
			theKeyName = keyName;
			return this;
		}

		protected ObservableConfigFormat<K> getKeyFormat() {
			ObservableConfigFormat<K> format = theKeyFormat;
			if (format == null)
				format = theValueBuilder.getFormatSet().getConfigFormat(theKeyType, theValueBuilder.getConfig().getName());
			return format;
		}

		protected String getKeyName() {
			return theKeyName == null ? "key" : theKeyName;
		}

		/** @deprecated Not yet implemented */
		@Deprecated
		public ObservableMap<K, V> buildMap(Consumer<ObservableMap<K, V>> preReturnGet) {
			return theValueBuilder.build(
				findRefs -> new ObservableConfigTransform.ObservableConfigMap<>(new ObservableConfigParseSession(),
					theValueBuilder.getConfig(), //
					theValueBuilder.getDescendant(true), theValueBuilder.createDescendant(true)::get, //
					getKeyName(), theValueBuilder.getChildName(), theKeyType, theValueBuilder.theType, getKeyFormat(),
					theValueBuilder.getFormat(), theValueBuilder.getUntil(), true, findRefs),
				preReturnGet);
		}

		/** @deprecated Not yet implemented */
		@Deprecated
		public ObservableMultiMap<K, V> buildMultiMap(Consumer<ObservableMultiMap<K, V>> preReturnGet) {
			return theValueBuilder
				.build(findRefs -> new ObservableConfigTransform.ObservableConfigMultiMap<>(new ObservableConfigParseSession(),
					theValueBuilder.getConfig(), //
					theValueBuilder.getDescendant(true), theValueBuilder.createDescendant(true)::get, //
					getKeyName(), theValueBuilder.getChildName(), theKeyType, theValueBuilder.theType, getKeyFormat(),
					theValueBuilder.getFormat(), theValueBuilder.getUntil(), true, findRefs), preReturnGet);
		}
	}

	default ObservableConfig getChild(String path) {
		return getChild(path, false, null);
	}

	default ObservableConfig getChild(String path, boolean createIfAbsent, Consumer<ObservableConfig> preAddMod) {
		return getChild(ObservableConfigPath.create(path), createIfAbsent, preAddMod);
	}

	default ObservableConfig getChild(ObservableConfigPath path, boolean createIfAbsent, Consumer<ObservableConfig> preAddMod) {
		if (path == null)
			throw new IllegalArgumentException("No path given");
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

	default ObservableConfig getChild(ObservableConfigPathElement el, boolean createIfAbsent, Consumer<ObservableConfig> preAddMod) {
		String pathName = el.getName();
		if (pathName.equals(ObservableConfigPath.ANY_NAME) || pathName.equals(ObservableConfigPath.ANY_DEPTH))
			throw new IllegalArgumentException("Variable paths not allowed for getChild");
		DefaultObservableConfig found = null;
		for (ObservableConfig config : getContent()) {
			if (el.matches(config)) {
				found = (DefaultObservableConfig) config;
				break;
			}
		}
		if (found == null && createIfAbsent) {
			found = (DefaultObservableConfig) addChild(pathName, ch -> {
				for (Map.Entry<String, String> attr : el.getAttributes().entrySet())
					ch.addChild(attr.getKey(), atCh -> atCh.setValue(attr.getValue()));
				if (preAddMod != null)
					preAddMod.accept(ch);
			});
		}
		return found;
	}

	default ObservableConfig addChild(String name) {
		return addChild(name, null);
	}

	default ObservableConfig addChild(String name, Consumer<ObservableConfig> preAddMod) {
		return addChild(null, null, false, name, preAddMod);
	}

	ObservableConfig addChild(ObservableConfig after, ObservableConfig before, boolean first, String name,
		Consumer<ObservableConfig> preAddMod);

	ObservableConfig moveChild(ObservableConfig child, ObservableConfig after, ObservableConfig before, boolean first,
		Runnable afterRemove);

	@Override
	ObservableConfig setName(String name);

	ObservableConfig setValue(String value);

	default ObservableConfig set(String path, String value) {
		ObservableConfig child = getChild(path, value != null, //
			ch -> ch.setValue(value));
		if (value == null) {
			if (child != null)
				child.remove();
		} else if (child.getValue() != value)
			child.setValue(value);
		return this;
	}

	ObservableConfig copyFrom(ObservableConfig source, boolean removeExtras);

	void remove();

	public static String toString(ObservableConfig config) {
		StringWriter out = new StringWriter();
		try {
			new XmlWriteHelper()._writeXml(config, out, new XmlEncoding(":", ":", ""), 0, "", false);
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
		return out.toString();
	}

	default String printXml() {
		StringWriter out = new StringWriter();
		try {
			new XmlWriteHelper()._writeXml(this, out, new XmlEncoding(":", ":", ""), 0, "\t", true);
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
		return out.toString();
	}

	default <E extends Exception> Subscription persistOnShutdown(ObservableConfigPersistence<E> persistence,
		Consumer<? super Exception> onException) {
		return persistWhen(Observable.onVmShutdown(), persistence, onException);
	}

	default <E extends Exception> Subscription persistEvery(Duration interval, ObservableConfigPersistence<E> persistence,
		Consumer<? super Exception> onException) {
		return persistWhen(Observable.<Void> every(Duration.ZERO, interval, null, d -> null, null), persistence, onException);
	}

	default <E extends Exception> Subscription persistOnChange(ObservableConfigPersistence<E> persistence,
		Consumer<? super Exception> onException) {
		return persistWhen(watch(ObservableConfigPath.buildPath("").multi(true).build()), persistence, onException);
	}

	default <E extends Exception> Subscription persistWhen(Observable<?> observable, ObservableConfigPersistence<E> persistence,
		Consumer<? super Exception> onException) {
		return observable.subscribe(new Observer<Object>() {
			private long theLastStamp = getStamp();

			@Override
			public <V> void onNext(V value) {
				tryPersist(false);
			}

			@Override
			public <V> void onCompleted(V value) {
				tryPersist(true);
			}

			private void tryPersist(boolean waitForLock) {
				if (getStamp() == theLastStamp)
					return; // No changes, don't re-persist
				Transaction lock = waitForLock ? lock(false, null) : tryLock(false, null);
				if (lock == null)
					return;
				try {
					theLastStamp = getStamp();
					persistence.persist(ObservableConfig.this);
				} catch (Exception ex) {
					onException.accept(ex);
				}
			}
		});
	}

	public static ObservableConfigPersistence<IOException> toFile(File file, XmlEncoding encoding) {
		return toWriter(() -> new BufferedWriter(new FileWriter(file)), encoding);
	}

	public interface WriterSupplier {
		Writer createWriter() throws IOException;
	}

	public static ObservableConfigPersistence<IOException> toWriter(WriterSupplier writer, XmlEncoding encoding) {
		return new ObservableConfig.ObservableConfigPersistence<IOException>() {
			@Override
			public void persist(ObservableConfig cfg) throws IOException {
				try (Writer w = writer.createWriter()) {
					ObservableConfig.writeXml(cfg, w, encoding, "\t");
				}
			}
		};
	}

	public static ObservableConfig createRoot(String name) {
		return DefaultObservableConfig.createRoot(name);
	}

	public static ObservableConfig createRoot(String name, String value, Function<Object, CollectionLockingStrategy> locking) {
		return DefaultObservableConfig.createRoot(name, value, locking);
	}

	public static class XmlEncoding {
		public static final XmlEncoding DEFAULT = new XmlEncoding("::", "::", "__", Collections.emptyMap()); // TODO

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
			if (xmlName.length() == 0)
				return emptyContent;
			MapEntryHandle<String, String> found = null;
			int i;
			for (i = 0; i < xmlName.length() && found == null; i++) {
				int offset = i;
				found = namedReplacements.search(r -> searchMatch(xmlName, r, offset), BetterSortedList.SortedSearchFilter.OnlyMatch);
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
					found = namedReplacements.search(r -> searchMatch(xmlName, r, offset), BetterSortedList.SortedSearchFilter.OnlyMatch);
				}
			} while (found != null);
			str.append(xmlName, i, xmlName.length());
			String encoded = str.toString();
			str.setLength(0);
			return encoded;
		}

		public String decode(String xmlName) {
			if (emptyContent.equals(xmlName))
				return "";
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
						newConfig = theStack.getLast().addChild(name);
						persistAttributes(newConfig, attributes);
					}
					theStack.add(newConfig);
					if (theContentStack.size() < theStack.size())
						theContentStack.add(new StringBuilder());
				}

				private void persistAttributes(ObservableConfig cfg, Attributes attributes) {
					for (int a = 0; a < attributes.getLength(); a++) {
						String attName = attributes.getLocalName(a);
						if (attName == null || attName.length() == 0)
							attName = attributes.getQName(a);
						cfg.set(encoding.decode(attName), encoding.decode(attributes.getValue(a)));
					}
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
			new XmlWriteHelper()._writeXml(config, out, encoding, 0, indent, true);
		}
		out.append('\n');
	}

	class XmlWriteHelper {
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
				else if (ch == '"')
					break;
				else if (ch == 0)
					throw new IllegalArgumentException("Cannot encode NUL");
				else if (ch < ' ' || ch > '~')
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
				else if (ch == '"')
					escapeTemp.append("&quot;");
				else if (ch == 0)
					throw new IllegalArgumentException("Cannot encode NUL");
				else if (ch < ' ' || ch > '~') {
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

		private void _writeXml(ObservableConfig config, Writer out, XmlEncoding encoding, int indentAmount, String indentStr,
			boolean withChildren) throws IOException {
			for (int i = 0; i < indentAmount; i++)
				out.append(indentStr);
			String xmlName = encoding.encode(config.getName());
			out.append('<').append(xmlName);

			// See if any of the children should be attributes
			String singular = StringUtils.singularize(config.getName());
			int i = 0;
			for (ObservableConfig child : config.getContent()) {
				boolean maybeAttr = mayBeAttribute(child);
				if (maybeAttr && child.getName().equals(singular))
					maybeAttr = false;
				if (maybeAttr) {
					Integer old = attrNames.put(child.getName(), i);
					if (old == null)
						childrenAsAttributes.set(i);
					else
						childrenAsAttributes.clear(old);
				}
				i++;
			}
			attrNames.clear();
			if (!childrenAsAttributes.isEmpty()) {
				for (i = childrenAsAttributes.nextSetBit(0); i >= 0; i = childrenAsAttributes.nextSetBit(i + 1)) {
					ObservableConfig child = config.getContent().get(i);
					out.append(' ').append(encoding.encode(child.getName())).append("=\"").append(escapeXml(child.getValue(), encoding))
					.append('"');
				}
			}
			if (withChildren)
				withChildren = childrenAsAttributes.cardinality() < config.getContent().size();
			if (!withChildren && config.getValue() == null) {
				out.append(" />");
				childrenAsAttributes.clear();
			} else {
				out.append(">");
				if (config.getValue() != null)
					out.append(escapeXml(config.getValue(), encoding));
				if (withChildren) {
					i = 0;
					BitSet copy = (BitSet) childrenAsAttributes.clone();
					childrenAsAttributes.clear();
					for (ObservableConfig child : config.getContent()) {
						if (!copy.get(i)) {
							out.append('\n');
							_writeXml(child, out, encoding, indentAmount + 1, indentStr, true);
						}
						i++;
					}

					out.append('\n');
					for (i = 0; i < indentAmount; i++)
						out.append(indentStr);
				} else
					childrenAsAttributes.clear();
				out.append("</").append(xmlName).append('>');
			}
		}

		protected boolean mayBeAttribute(ObservableConfig config) {
			String value = config.getValue();
			if (value == null || value.length() == 0 || !config.getContent().isEmpty())
				return false;
			for (int c = 0; c < value.length(); c++)
				if (value.charAt(c) < ' ' || value.charAt(c) > '~')
					return false;
			return true;
		}
	}
}
