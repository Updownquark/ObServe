package org.observe.config;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

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
import org.observe.config.ObservableConfig.ObservableConfigEvent;
import org.observe.config.ObservableConfig.ObservableConfigPath;
import org.observe.config.ObservableConfig.ObservableConfigPathElement;
import org.observe.util.TypeTokens;
import org.qommons.Causable;
import org.qommons.Identifiable;
import org.qommons.Identifiable.AbstractIdentifiable;
import org.qommons.QommonsUtils;
import org.qommons.Stamped;
import org.qommons.Transaction;
import org.qommons.collect.BetterCollection;
import org.qommons.collect.BetterList;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.ElementId;
import org.qommons.collect.ListenerList;
import org.qommons.collect.MutableCollectionElement;
import org.qommons.collect.MutableCollectionElement.StdMsg;
import org.qommons.collect.QuickSet;
import org.qommons.collect.QuickSet.QuickMap;

import com.google.common.reflect.TypeToken;

/** Contains implementation classes for {@link ObservableConfig} methods */
public class ObservableConfigContent {
	/**
	 * Observes a config's descendant at a path
	 *
	 * @param <C> The sub-type of config
	 */
	protected static class ObservableConfigChild<C extends ObservableConfig> extends AbstractIdentifiable implements ObservableValue<C> {
		private final TypeToken<C> theType;
		private final ObservableConfig theRoot;
		private final ObservableConfigPath thePath;
		private final ObservableConfig[] thePathElements;
		private final long[] thePathElementStamps;
		private Subscription thePathSubscription;
		private final ListenerList<Observer<? super ObservableValueEvent<C>>> theListeners;
		private Object theChangesIdentity;

		/**
		 * @param type The sub-type of config
		 * @param root The root config to observe
		 * @param path The path of the descendant to observe
		 */
		public ObservableConfigChild(TypeToken<C> type, ObservableConfig root, ObservableConfigPath path) {
			theType = type;
			theRoot = root;
			thePath = path;
			for (ObservableConfigPathElement el : path.getElements()) {
				if (el.isMulti())
					throw new IllegalArgumentException("Cannot use observeValue with a variable path");
			}
			thePathElements = new ObservableConfig[path.getElements().size()];
			thePathElementStamps = new long[path.getElements().size()];
			theListeners = ListenerList.build().withInUse(this::setInUse).build();
		}

		ObservableConfig getRoot() {
			return theRoot;
		}

		void setInUse(boolean inUse) {
			try (Transaction t = theRoot.lock(false, null)) {
				if (inUse) {
					thePathSubscription = theRoot.watch(ObservableConfig.ANY_DEPTH).act(evt -> {
						int size = evt.relativePath.size();
						if (size > thePathElements.length)
							return;
						int pathIndex = 0;
						ObservableConfigEvent pathChange = evt;
						boolean childChange = false;
						while (pathIndex < thePathElements.length && pathIndex < evt.relativePath.size()) {
							if (evt.relativePath.get(pathIndex) != thePathElements[pathIndex])
								break;
							childChange = true;
							pathChange = pathChange.asFromChild();
							thePathElementStamps[pathIndex] = thePathElements[pathIndex].getStamp();
							pathIndex++;
						}
						try (Transaction ct = childChange ? Causable.use(pathChange) : Transaction.NONE) {
							handleChange(pathIndex, pathChange, evt);
						}
					});
				} else {
					thePathSubscription.unsubscribe();
					thePathSubscription = null;
				}
			}
		}

		void handleChange(int pathIndex, ObservableConfigEvent pathChange, ObservableConfigEvent evt) {
			boolean checkForChange = false;
			switch (evt.changeType) {
			case add:
				if (pathIndex >= thePathElements.length
				|| !thePath.getElements().get(pathIndex).matchedBefore(pathChange.eventTarget, pathChange)) {
					checkForChange = true;
				} else if (thePathElements[pathIndex] == null
					|| pathChange.eventTarget.getParentChildRef().compareTo(thePathElements[pathIndex].getParentChildRef()) < 0) {
					ObservableConfig oldValue = thePathElements[thePathElements.length - 1];
					thePathElements[pathIndex] = evt.relativePath.get(pathIndex);
					thePathElementStamps[pathIndex] = -1;
					if (pathIndex < thePathElements.length - 1)
						resolvePath(pathIndex + 1, false);
					ObservableConfig newValue = thePathElements[thePathElements.length - 1];
					fire(createChangeEvent((C) oldValue, (C) newValue, evt));
				}
				break;
			case remove:
				if (evt.relativePath.isEmpty())
					return; // Removed the root, but we'll keep listening to it
				if (pathIndex == evt.relativePath.size()) {
					pathIndex--;
					ObservableConfig oldValue = thePathElements[thePathElements.length - 1];
					Arrays.fill(thePathElements, pathIndex, thePathElements.length, null);
					thePathElementStamps[pathIndex] = -1;
					resolvePath(pathIndex, false);
					ObservableConfig newValue = thePathElements[thePathElements.length - 1];
					if (oldValue != newValue)
						fire(createChangeEvent((C) oldValue, (C) newValue, evt));
				} else
					checkForChange = true;
				break;
			case set:
				if (pathIndex == 0)
					return; // Something about the root changed, but that can't affect us
				pathIndex--;
				if (!thePath.getElements().get(pathIndex).matches(thePathElements[pathIndex])) {
					ObservableConfig oldValue = thePathElements[thePathElements.length - 1];
					Arrays.fill(thePathElements, pathIndex, thePathElements.length, null);
					resolvePath(pathIndex, false);
					ObservableConfig newValue = thePathElements[thePathElements.length - 1];
					if (oldValue != newValue)
						fire(createChangeEvent((C) oldValue, (C) newValue, evt));
				} else
					checkForChange = true;
				break;
			}
			if (checkForChange) {
				ObservableConfig oldValue = thePathElements[thePathElements.length - 1];
				resolvePath(pathIndex, false);
				ObservableConfig newValue = thePathElements[thePathElements.length - 1];
				if (oldValue != newValue)
					fire(createChangeEvent((C) oldValue, (C) newValue, evt));
			}
		}

		private void fire(ObservableValueEvent<C> event) {
			try (Transaction t = Causable.use(event)) {
				theListeners.forEach(//
					listener -> listener.onNext(event));
			}
		}

		@Override
		public TypeToken<C> getType() {
			return theType;
		}

		@Override
		public long getStamp() {
			resolvePath(0, false);
			return Stamped.compositeStamp(thePathElementStamps);
		}

		@Override
		protected Object createIdentity() {
			return Identifiable.wrap(theRoot, "descendant", thePath);
		}

		@Override
		public C get() {
			try (Transaction t = lock()) {
				resolvePath(0, false);
				return (C) thePathElements[thePathElements.length - 1];
			}
		}

		boolean resolvePath(int startIndex, boolean createIfAbsent) {
			ObservableConfig parent = startIndex == 0 ? theRoot : thePathElements[startIndex - 1];
			boolean resolved = true;
			boolean changed = false;
			int i;
			for (i = startIndex; i < thePathElements.length; i++) {
				long stamp = parent.getStamp();
				ObservableConfig child;
				if (thePathElementStamps[i] == stamp) {
					// No need to check--nothing's changed
					child = thePathElements[i];
					if (child == null && createIfAbsent) {
						child = parent.getChild(thePath.getElements().get(i), createIfAbsent, null);
						thePathElementStamps[i] = stamp;
					}
				} else {
					child = parent.getChild(thePath.getElements().get(i), createIfAbsent, null);
					thePathElementStamps[i] = stamp;
					if (thePathElements[i] != child) {
						changed = true;
						thePathElements[i] = child;
					}
				}
				if (child == null) {
					resolved = false;
					break;
				} else
					parent = child;
			}
			if (!resolved)
				Arrays.fill(thePathElements, i, thePathElements.length, null);
			return changed;
		}

		@Override
		public Observable<ObservableValueEvent<C>> noInitChanges() {
			return new Observable<ObservableValueEvent<C>>() {
				@Override
				public Object getIdentity() {
					if (theChangesIdentity == null)
						theChangesIdentity = Identifiable.wrap(ObservableConfigChild.this.getIdentity(), "noInitChanges");
					return theChangesIdentity;
				}

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

	/** Observes the value of a config's path descendant */
	protected static class ObservableConfigValue extends AbstractIdentifiable implements SettableValue<String> {
		private final ObservableConfigChild<ObservableConfig> theConfigChild;
		private Object theChangesIdentity;

		/**
		 * @param root The root config to observe
		 * @param path The path of the config's descendant to observe the value of
		 */
		public ObservableConfigValue(ObservableConfig root, ObservableConfigPath path) {
			theConfigChild = new ObservableConfigChild<>(ObservableConfig.TYPE, root, path);
		}

		@Override
		public TypeToken<String> getType() {
			return TypeTokens.get().STRING;
		}

		@Override
		public long getStamp() {
			long stamp = theConfigChild.getStamp();
			ObservableConfig child = theConfigChild.get();
			if (child != null)
				stamp ^= Long.rotateRight(child.getStamp(), 32);
			return stamp;
		}

		@Override
		protected Object createIdentity() {
			return Identifiable.wrap(theConfigChild.getIdentity(), "value");
		}

		@Override
		public String get() {
			try (Transaction t = lock()) {
				return parse(theConfigChild.get());
			}
		}

		private String parse(ObservableConfig config) {
			return config == null ? null : config.getValue();
		}

		@Override
		public Observable<ObservableValueEvent<String>> noInitChanges() {
			class Changes extends AbstractIdentifiable implements Observable<ObservableValueEvent<String>> {
				@Override
				protected Object createIdentity() {
					if(theChangesIdentity==null)
						theChangesIdentity = Identifiable.wrap(ObservableConfigValue.this.getIdentity(), "noInitChanges");
					return theChangesIdentity;
				}

				@Override
				public boolean isSafe() {
					return true;
				}

				@Override
				public Transaction lock() {
					return theConfigChild.theRoot.lock(false, null);
				}

				@Override
				public Transaction tryLock() {
					return theConfigChild.theRoot.tryLock(false, null);
				}

				@Override
				public Subscription subscribe(Observer<? super ObservableValueEvent<String>> observer) {
					try (Transaction t = theConfigChild.theRoot.lock(false, null)) {
						Subscription[] configSub = new Subscription[1];
						Subscription valueSub = theConfigChild.changes().act(evt -> {
							if (configSub[0] != null) {
								configSub[0].unsubscribe();
								configSub[0] = null;
							}
							ObservableConfig newConfig = evt.getNewValue();
							try (Transaction configT = newConfig == null ? Transaction.NONE : newConfig.lock(false, null)) {
								observer.onNext(createChangeEvent(parse(evt.getOldValue()), parse(newConfig), evt));
								if (newConfig != null)
									configSub[0] = newConfig.watch("").act(configEvt -> {
										if (!configEvt.relativePath.isEmpty())
											return;
										observer.onNext(createChangeEvent(configEvt.oldValue, parse(newConfig), configEvt));
									});
							}
						});
						return () -> {
							valueSub.unsubscribe();
							if (configSub[0] != null)
								configSub[0].unsubscribe();
						};
					}
				}
			}
			return new Changes();
		}

		@Override
		public String set(String value, Object cause) throws IllegalArgumentException, UnsupportedOperationException {
			try (Transaction t = theConfigChild.getRoot().lock(true, cause)) {
				String msg = isAcceptable(value);
				if (msg != null)
					throw new IllegalArgumentException(msg);
				theConfigChild.resolvePath(0, true);
				String oldValue = parse(theConfigChild.get());
				theConfigChild.get().setValue(value);
				return oldValue;
			}
		}

		@Override
		public String isAcceptable(String value) {
			return null;
		}

		@Override
		public ObservableValue<String> isEnabled() {
			return SettableValue.ALWAYS_ENABLED;
		}
	}

	/**
	 * Superclass to assist in implementing the collection behind {@link ObservableConfig#getContent(ObservableConfigPath)}
	 *
	 * @param <C> The config sub-type
	 */
	protected static abstract class AbstractObservableConfigContent<C extends ObservableConfig> implements ObservableCollection<C> {
		private final ObservableConfig theConfig;
		private final TypeToken<C> theType;

		/**
		 * @param config The root config
		 * @param type The config sub-type
		 */
		public AbstractObservableConfigContent(ObservableConfig config, TypeToken<C> type) {
			theConfig = config;
			theType = type;
		}

		/** @return The root config */
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
		public long getStamp() {
			return theConfig.getStamp();
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return theConfig.lock(write, cause);
		}

		@Override
		public Transaction tryLock(boolean write, Object cause) {
			return theConfig.tryLock(write, cause);
		}

		@Override
		public Equivalence<? super C> equivalence() {
			return Equivalence.DEFAULT;
		}
	}

	/**
	 * Implements the collection behind {@link ObservableConfig#getAllContent()}
	 *
	 * @param <C> The config sub-type
	 */
	protected static class FullObservableConfigContent<C extends ObservableConfig> extends AbstractObservableConfigContent<C> {
		private Object theIdentity;

		/**
		 * @param config The parent config
		 * @param type The config sub-type
		 */
		public FullObservableConfigContent(ObservableConfig config, TypeToken<C> type) {
			super(config, type);
		}

		@Override
		public Object getIdentity() {
			if (theIdentity == null)
				theIdentity = Identifiable.wrap(getConfig(), "allContent");
			return theIdentity;
		}

		@Override
		public void clear() {
			try (Transaction t = getConfig().lock(true, null)) {
				ObservableConfig lastChild = getConfig()._getContent().getLast();
				while (lastChild != null) {
					ObservableConfig nextLast = CollectionElement
						.get(getConfig()._getContent().getAdjacentElement(lastChild.getParentChildRef(), false));
					lastChild.remove();
					lastChild = nextLast;
				}
			}
		}

		@Override
		public int size() {
			return getConfig()._getContent().size();
		}

		@Override
		public boolean isEmpty() {
			return getConfig()._getContent().isEmpty();
		}

		@Override
		public CollectionElement<C> getElement(int index) {
			ObservableConfig child = getConfig()._getContent().get(index);
			return new ConfigCollectionElement<>(child);
		}

		@Override
		public int getElementsBefore(ElementId id) {
			return getConfig()._getContent().getElementsBefore(id);
		}

		@Override
		public int getElementsAfter(ElementId id) {
			return getConfig()._getContent().getElementsAfter(id);
		}

		@Override
		public CollectionElement<C> getElement(C value, boolean first) {
			ObservableConfig config = CollectionElement.get(getConfig()._getContent().getElement(value, first));
			return config == null ? null : new ConfigCollectionElement<>(config);
		}

		@Override
		public CollectionElement<C> getElement(ElementId id) {
			ObservableConfig config = CollectionElement.get(getConfig()._getContent().getElement(id));
			return new ConfigCollectionElement<>(config);
		}

		@Override
		public CollectionElement<C> getTerminalElement(boolean first) {
			ObservableConfig config = CollectionElement.get(getConfig()._getContent().getTerminalElement(first));
			return config == null ? null : new ConfigCollectionElement<>(config);
		}

		@Override
		public CollectionElement<C> getAdjacentElement(ElementId elementId, boolean next) {
			ObservableConfig config = CollectionElement.get(getConfig()._getContent().getAdjacentElement(elementId, next));
			return config == null ? null : new ConfigCollectionElement<>(config);
		}

		@Override
		public MutableCollectionElement<C> mutableElement(ElementId id) {
			ObservableConfig config = CollectionElement.get(getConfig()._getContent().getElement(id));
			return new MutableConfigCollectionElement<>(config, this);
		}

		@Override
		public BetterList<CollectionElement<C>> getElementsBySource(ElementId sourceEl) {
			return QommonsUtils.map2(getConfig()._getContent().getElementsBySource(sourceEl),
				el -> new ConfigCollectionElement<>(el.get()));
		}

		@Override
		public BetterList<ElementId> getSourceElements(ElementId localElement, BetterCollection<?> sourceCollection) {
			if (sourceCollection == this)
				return getConfig()._getContent().getSourceElements(localElement, getConfig()._getContent());
			return getConfig()._getContent().getSourceElements(localElement, sourceCollection);
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
		public String canMove(ElementId valueEl, ElementId after, ElementId before) {
			return null;
		}

		@Override
		public CollectionElement<C> move(ElementId valueEl, ElementId after, ElementId before, boolean first, Runnable afterRemove)
			throws UnsupportedOperationException, IllegalArgumentException {
			ObservableConfig valueConfig = getConfig()._getContent().getElement(valueEl).get();
			ObservableConfig afterConfig = after == null ? null : getConfig()._getContent().getElement(after).get();
			ObservableConfig beforeConfig = before == null ? null : getConfig()._getContent().getElement(before).get();
			return new ConfigCollectionElement<>(getConfig()//
				.moveChild(valueConfig, afterConfig, beforeConfig, first, afterRemove));
		}

		@Override
		public void setValue(Collection<ElementId> elements, C value) {
			throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
		}

		@Override
		public Subscription onChange(Consumer<? super ObservableCollectionEvent<? extends C>> observer) {
			return getConfig().watch(getConfig().createPath(ObservableConfig.ANY_NAME)).act(evt -> {
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
			return theConfig.getParentChildRef();
		}

		@Override
		public C get() {
			return (C) theConfig;
		}

		@Override
		public int hashCode() {
			return theConfig.getParentChildRef().hashCode();
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
		private final AbstractObservableConfigContent<C> theCollection;

		MutableConfigCollectionElement(ObservableConfig config, AbstractObservableConfigContent<C> collection) {
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
			if (value == get()) {
				value.update();
			} else
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

	/**
	 * Implements the collection behind {@link ObservableConfig#getContent(ObservableConfigPath)} for single-element paths
	 *
	 * @param <C> The sub-type of config
	 */
	protected static class SimpleObservableConfigContent<C extends ObservableConfig> extends AbstractObservableConfigContent<C> {
		private final ObservableConfigPathElement thePathElement;
		private Object theIdentity;

		/**
		 * @param config The parent config
		 * @param type The config sub-type
		 * @param pathEl The path element
		 */
		public SimpleObservableConfigContent(ObservableConfig config, TypeToken<C> type, ObservableConfigPathElement pathEl) {
			super(config, type);
			thePathElement = pathEl;
		}

		@Override
		public Object getIdentity() {
			if (theIdentity == null)
				theIdentity = Identifiable.wrap(getConfig(), "content", thePathElement);
			return theIdentity;
		}

		@Override
		public int size() {
			try (Transaction t = getConfig().lock(false, null)) {
				return (int) getConfig()._getContent().stream().filter(thePathElement::matches).count();
			}
		}

		@Override
		public boolean isEmpty() {
			try (Transaction t = getConfig().lock(false, null)) {
				return getConfig()._getContent().stream().anyMatch(thePathElement::matches);
			}
		}

		@Override
		public CollectionElement<C> getElement(int index) {
			try (Transaction t = getConfig().lock(false, null)) {
				int i = 0;
				for (CollectionElement<ObservableConfig> el : getConfig()._getContent().elements()) {
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
				int idx = getConfig()._getContent().getElementsBefore(id);
				int i = 0;
				int matches = 0;
				for (CollectionElement<ObservableConfig> el : getConfig()._getContent().elements()) {
					if (i == idx)
						break;
					if (thePathElement.matches(el.get()))
						matches++;
					i++;
				}
				return matches;
			}
		}

		@Override
		public int getElementsAfter(ElementId id) {
			try (Transaction t = getConfig().lock(false, null)) {
				ObservableConfig config = CollectionElement.get(getConfig()._getContent().getElement(id));
				if (config == null || !thePathElement.matches(config))
					throw new NoSuchElementException();
				int i = 0;
				for (CollectionElement<ObservableConfig> el : getConfig()._getContent().reverse().elements()) {
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
				ObservableConfig config = CollectionElement.get(getConfig()._getContent().getElement(value, first));
				return config == null ? null : new ConfigCollectionElement<>(config);
			}
		}

		@Override
		public CollectionElement<C> getElement(ElementId id) {
			try (Transaction t = getConfig().lock(false, null)) {
				ObservableConfig config = CollectionElement.get(getConfig()._getContent().getElement(id));
				if (!thePathElement.matches(config))
					throw new NoSuchElementException();
				return new ConfigCollectionElement<>(config);
			}
		}

		@Override
		public BetterList<CollectionElement<C>> getElementsBySource(ElementId sourceEl) {
			return QommonsUtils.filterMap(getConfig()._getContent().getElementsBySource(sourceEl), el -> thePathElement.matches(el.get()),
				el -> new ConfigCollectionElement<>(el.get()));
		}

		@Override
		public BetterList<ElementId> getSourceElements(ElementId localElement, BetterCollection<?> sourceCollection) {
			if (sourceCollection == this)
				return getConfig()._getContent().getSourceElements(localElement, getConfig()._getContent());
			return getConfig()._getContent().getSourceElements(localElement, sourceCollection);
		}

		@Override
		public CollectionElement<C> getTerminalElement(boolean first) {
			try (Transaction t = getConfig().lock(false, null)) {
				ObservableConfig config = CollectionElement.get(getConfig()._getContent().getTerminalElement(first));
				while (config != null && !thePathElement.matches(config))
					config = CollectionElement.get(getConfig()._getContent().getAdjacentElement(config.getParentChildRef(), first));
				return config == null ? null : new ConfigCollectionElement<>(config);
			}
		}

		@Override
		public CollectionElement<C> getAdjacentElement(ElementId elementId, boolean next) {
			try (Transaction t = getConfig().lock(false, null)) {
				ObservableConfig config = CollectionElement.get(getConfig()._getContent().getAdjacentElement(elementId, next));
				while (config != null && !thePathElement.matches(config))
					config = CollectionElement.get(getConfig()._getContent().getAdjacentElement(config.getParentChildRef(), next));
				return config == null ? null : new ConfigCollectionElement<>(config);
			}
		}

		@Override
		public MutableCollectionElement<C> mutableElement(ElementId id) {
			try (Transaction t = getConfig().lock(false, null)) {
				ObservableConfig config = CollectionElement.get(getConfig()._getContent().getElement(id));
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
		public String canMove(ElementId valueEl, ElementId after, ElementId before) {
			return null;
		}

		@Override
		public CollectionElement<C> move(ElementId valueEl, ElementId after, ElementId before, boolean first, Runnable afterRemove)
			throws UnsupportedOperationException, IllegalArgumentException {
			ObservableConfig valueConfig = getConfig()._getContent().getElement(valueEl).get();
			ObservableConfig afterConfig = after == null ? null : getConfig()._getContent().getElement(after).get();
			ObservableConfig beforeConfig = before == null ? null : getConfig()._getContent().getElement(before).get();
			return new ConfigCollectionElement<>(getConfig()//
				.moveChild(valueConfig, afterConfig, beforeConfig, first, afterRemove));
		}

		@Override
		public void setValue(Collection<ElementId> elements, C value) {
			throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
		}

		@Override
		public void clear() {
			try (Transaction t = getConfig().lock(true, null)) {
				for (CollectionElement<ObservableConfig> el : getConfig()._getContent().elements()) {
					if (thePathElement.matches(el.get()))
						el.get().remove();
				}
			}
		}

		@Override
		public Subscription onChange(Consumer<? super ObservableCollectionEvent<? extends C>> observer) {
			String watchPath = thePathElement.getName() + ObservableConfig.PATH_SEPARATOR + ObservableConfig.ANY_DEPTH;
			return getConfig().watch(getConfig().createPath(watchPath)).act(evt -> {
				C child = (C) evt.relativePath.get(0);
				boolean postMatches = thePathElement.matches(child);
				boolean preMatches = evt.changeType == CollectionChangeType.set ? thePathElement.matchedBefore(child, evt)
					: postMatches;
				if (preMatches || postMatches) {
					int index;
					if (postMatches)
						index = getElementsBefore(child.getParentChildRef());
					else {
						int i = 0;
						for (CollectionElement<ObservableConfig> el : getConfig()._getContent().elements()) {
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
					if (evt.relativePath.size() > 1)
						changeType = CollectionChangeType.set;
					else if (preMatches && postMatches)
						changeType = evt.changeType;
					else if (preMatches)
						changeType = CollectionChangeType.remove;
					else
						changeType = CollectionChangeType.add;

					C oldValue = changeType == CollectionChangeType.add ? null : child;
					ObservableCollectionEvent<C> collEvt = new ObservableCollectionEvent<>(child.getParentChildRef(), getType(), index,
						changeType, oldValue, child, evt);
					try (Transaction t = Causable.use(collEvt)) {
						observer.accept(collEvt);
					}
				}
			});
		}
	}

	/**
	 * Implements the value set portion of {@link ObservableConfig#getContent(ObservableConfigPath)}
	 *
	 * @param <C> The sub-type of config
	 */
	protected static class ObservableChildSet<C extends ObservableConfig> implements ObservableValueSet<C> {
		private final ObservableConfig theRoot;
		private final ObservableConfigPath thePath;
		private final ObservableCollection<C> theChildren;

		/**
		 * @param root The root config
		 * @param path The path for the values
		 * @param children The child collection
		 */
		public ObservableChildSet(ObservableConfig root, ObservableConfigPath path, ObservableCollection<C> children) {
			theRoot = root;
			thePath = path;
			theChildren = children;
		}

		/** @return The root config */
		protected ObservableConfig getRoot() {
			return theRoot;
		}

		/** @return the path for the values */
		protected ObservableConfigPath getPath() {
			return thePath;
		}

		@Override
		public ConfiguredValueType<C> getType() {
			return new ConfiguredValueType<C>() {
				@Override
				public TypeToken<C> getType() {
					return theChildren.getType();
				}

				@Override
				public QuickMap<String, ConfiguredValueField<? super C, ?>> getFields() {
					return QuickSet.<String> empty().createMap();
				}

				@Override
				public <F> ConfiguredValueField<? super C, F> getField(Function<? super C, F> fieldGetter) {
					throw new UnsupportedOperationException("No typed fields for an " + ObservableConfig.class.getSimpleName());
				}

				@Override
				public boolean allowsCustomFields() {
					return true;
				}
			};
		}

		@Override
		public ObservableCollection<? extends C> getValues() {
			return theChildren;
		}

		@Override
		public <C2 extends C> ValueCreator<C, C2> create(TypeToken<C2> subType) {
			if (subType != getType().getType())
				throw new IllegalArgumentException("Unrecognized " + ObservableConfig.class.getSimpleName() + " sub-type " + subType);
			return (ValueCreator<C, C2>) new ValueCreator<C, C>() {
				private Map<String, String> theFields;
				private ElementId theAfter;
				private ElementId theBefore;
				private boolean isTowardBeginning;

				@Override
				public ConfiguredValueType<C> getType() {
					return ObservableChildSet.this.getType();
				}

				@Override
				public Set<Integer> getRequiredFields() {
					return Collections.emptySet();
				}

				@Override
				public ValueCreator<C, C> after(ElementId after) {
					theAfter = after;
					return this;
				}

				@Override
				public ValueCreator<C, C> before(ElementId before) {
					theBefore = before;
					return this;

				}

				@Override
				public ValueCreator<C, C> towardBeginning(boolean towardBeginning) {
					isTowardBeginning = towardBeginning;
					return this;
				}

				@Override
				public ValueCreator<C, C> with(String fieldName, Object value) throws IllegalArgumentException {
					if (theFields == null)
						theFields = new LinkedHashMap<>();
					theFields.put(fieldName, String.valueOf(value));
					return this;
				}

				@Override
				public <F> ValueCreator<C, C> with(ConfiguredValueField<? super C, F> field, F value) throws IllegalArgumentException {
					throw new UnsupportedOperationException();
				}

				@Override
				public <F> ValueCreator<C, C> with(Function<? super C, F> field, F value) throws IllegalArgumentException {
					throw new UnsupportedOperationException();
				}

				@Override
				public CollectionElement<C> create(Consumer<? super C> preAddAction) {
					ObservableConfig afterChild = theAfter == null ? null : theChildren.getElement(theAfter).get();
					ObservableConfig beforeChild = theBefore == null ? null : theChildren.getElement(theBefore).get();
					ElementId newChildId;
					try (Transaction t = theRoot.lock(true, null)) {
						ObservableConfig parent = thePath.getParent() == null ? theRoot : theRoot.getChild(thePath.getParent(), true, null);
						ObservableConfig newChild = parent.addChild(afterChild, beforeChild, isTowardBeginning,
							thePath.getLastElement().getName(),
							cfg -> {
								if (theFields != null)
									for (Map.Entry<String, String> field : theFields.entrySet())
										cfg.set(field.getKey(), field.getValue());
								if (preAddAction != null)
									preAddAction.accept((C) cfg);
							});
						newChildId = theChildren.getElementsBySource(newChild.getParentChildRef()).getFirst().getElementId();
					}
					return theChildren.getElement(newChildId);
				}
			};
		}

		@Override
		public <E2 extends C> CollectionElement<C> copy(E2 template) {
			return create().create(c -> {
				copy(template, c);
			});
		}

		static void copy(ObservableConfig source, ObservableConfig dest) {
			dest.setValue(source.getValue());
			for (int i = 0; i < source._getContent().size(); i++) {
				ObservableConfig srcChild = source._getContent().get(i);
				ObservableConfig destChild = dest.addChild(srcChild.getName());
				copy(srcChild, destChild);
			}
		}
	}
}
