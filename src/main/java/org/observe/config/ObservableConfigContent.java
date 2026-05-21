package org.observe.config;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

import org.observe.Equivalence;
import org.observe.Observable;
import org.observe.Observable.CoreChangeSources;
import org.observe.ObservableValue;
import org.observe.ObservableValueEvent;
import org.observe.Observer;
import org.observe.SettableValue;
import org.observe.collect.CollectionChangeType;
import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableCollectionEvent;
import org.observe.config.ObservableConfig.ObservableConfigEvent;
import org.observe.config.ObservableConfigPath.ObservableConfigPathElement;
import org.observe.util.TypeTokens;
import org.qommons.Identifiable;
import org.qommons.Identifiable.AbstractIdentifiable;
import org.qommons.QommonsUtils;
import org.qommons.Stamped;
import org.qommons.Subscription;
import org.qommons.ThreadConstraint;
import org.qommons.Transaction;
import org.qommons.collect.BetterCollection;
import org.qommons.collect.BetterList;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.ElementId;
import org.qommons.collect.ListElement;
import org.qommons.collect.ListenerList;
import org.qommons.collect.MutableCollectionElement.StdMsg;
import org.qommons.collect.MutableListElement;
import org.qommons.collect.QuickSet;
import org.qommons.collect.QuickSet.QuickMap;

import com.google.common.reflect.TypeToken;

/** Contains implementation classes for {@link ObservableConfig} methods */
public class ObservableConfigContent {
	/** Observes a config's descendant at a path */
	protected static class ObservableConfigChild extends AbstractIdentifiable implements ObservableValue<ObservableConfig> {
		private final ObservableConfig theRoot;
		private final ObservableConfigPath thePath;
		private final ObservableConfig[] thePathElements;
		private final long[] thePathElementStamps;
		private Subscription thePathSubscription;
		private final ListenerList<Observer<? super ObservableValueEvent<ObservableConfig>>> theListeners;

		/**
		 * @param root The root config to observe
		 * @param path The path of the descendant to observe
		 */
		public ObservableConfigChild(ObservableConfig root, ObservableConfigPath path) {
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
			try (Transaction t = theRoot.lock(false)) {
				if (inUse) {
					thePathSubscription = theRoot.watch(ObservableConfigPath.ANY_DEPTH)
						.act(Observer.<ObservableConfigEvent> printableObserver(evt -> {
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
							try (Transaction ct = childChange ? pathChange.use() : Transaction.NONE) {
								handleChange(pathIndex, pathChange, evt);
							}
						}, thePath::toString, null));
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
					fire(createChangeEvent(oldValue, newValue, evt));
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
						fire(createChangeEvent(oldValue, newValue, evt));
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
						fire(createChangeEvent(oldValue, newValue, evt));
				} else
					checkForChange = true;
				break;
			}
			if (checkForChange) {
				ObservableConfig oldValue = thePathElements[thePathElements.length - 1];
				resolvePath(pathIndex, false);
				ObservableConfig newValue = thePathElements[thePathElements.length - 1];
				if (oldValue != newValue)
					fire(createChangeEvent(oldValue, newValue, evt));
			}
		}

		private void fire(ObservableValueEvent<ObservableConfig> event) {
			try (Transaction t = event.use()) {
				theListeners.forEach(//
					listener -> listener.onNext(event));
			}
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
		public ObservableConfigChild alias(String alias) {
			super.alias(alias);
			return this;
		}

		@Override
		public ObservableConfig get() {
			return get(true);
		}

		private ObservableConfig get(boolean withLock) {
			// First, just see if we're already up-to-date so we don't have to do any locking
			ObservableConfig parent = theRoot;
			boolean found = true;
			for (int i = 0; i < thePathElements.length && parent != null && found; i++) {
				long stamp = parent.getStamp();
				if (thePathElementStamps[i] == stamp)
					parent = thePathElements[i];
				else
					found = false;
			}
			if (found)
				return parent;
			try (Transaction t = withLock ? lock(false) : Transaction.NONE) {
				resolvePath(0, false);
				return thePathElements[thePathElements.length - 1];
			}
		}

		@Override
		public Getter<ObservableConfig> lock(boolean tryOnly) {
			Transaction lock = theRoot.lock(tryOnly);
			if (lock == null)
				return null;
			return new Getter<ObservableConfig>() {
				@Override
				public ObservableConfig get() {
					return ObservableConfigChild.this.get(false);
				}

				@Override
				public void close() {
					lock.close();
				}
			};
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
						stamp = parent.getStamp();
						thePathElementStamps[i] = stamp;
					}
				} else {
					child = parent.getChild(thePath.getElements().get(i), createIfAbsent, null);
					if (thePathElements[i] != child) {
						if (createIfAbsent)
							stamp = parent.getStamp();
						changed = true;
						thePathElements[i] = child;
					}
					thePathElementStamps[i] = stamp;
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

		String canResolvePath(int startIndex, boolean createIfAbsent) {
			ObservableConfig parent = startIndex == 0 ? theRoot : thePathElements[startIndex - 1];
			boolean resolved = true;
			int i;
			for (i = startIndex; i < thePathElements.length; i++) {
				long stamp = parent.getStamp();
				ObservableConfig child;
				if (thePathElementStamps[i] == stamp) {
					// No need to check--nothing's changed
					child = thePathElements[i];
					if (child == null && createIfAbsent) {
						child = parent.getChild(thePath.getElements().get(i), false, null);
						if (child == null)
							return parent.canAddChild(null, null);
						thePathElementStamps[i] = stamp;
					}
				} else {
					child = parent.getChild(thePath.getElements().get(i), false, null);
					if (child == null)
						return parent.canAddChild(null, null);
					thePathElementStamps[i] = stamp;
					if (thePathElements[i] != child) {
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
			return null;
		}

		@Override
		public Observable<ObservableValueEvent<ObservableConfig>> noInitChanges() {
			class ConfigChildChanges extends AbstractIdentifiable implements Observable<ObservableValueEvent<ObservableConfig>> {
				@Override
				protected Object createIdentity() {
					return Identifiable.wrap(ObservableConfigChild.this.getIdentity(), "noInitChanges");
				}

				@Override
				public Subscription subscribe(Observer<? super ObservableValueEvent<ObservableConfig>> observer) {
					return theListeners.add(observer, true);
				}

				@Override
				public ThreadConstraint getThreadConstraint() {
					return theRoot.getThreadConstraint();
				}

				@Override
				public boolean isEventing() {
					return theRoot.isEventing();
				}

				@Override
				public Transaction lock(boolean tryOnly) {
					return theRoot.lock(tryOnly);
				}

				@Override
				public CoreId getCoreId() {
					return theRoot.getCoreId();
				}

				@Override
				public long getStamp() {
					return ObservableConfigChild.this.getStamp();
				}

				@Override
				public CoreChangeSources getChangeSources() {
					return theRoot.getChangeSources();
				}
			}
			return new ConfigChildChanges();
		}

		@Override
		public boolean isEventing() {
			return theRoot.isEventing();
		}
	}

	/** Observes the value of a config's path descendant */
	protected static class ObservableConfigValue extends SettableValue.SettableFlattenedObservableValue<String> {
		private final ObservableConfigChild theConfigChild;

		/**
		 * @param root The root config to observe
		 * @param path The path of the config's descendant to observe the value of
		 */
		public ObservableConfigValue(ObservableConfig root, ObservableConfigPath path) {
			this(new ObservableConfigChild(root, path));
		}

		private ObservableConfigValue(ObservableConfigChild configChild) {
			super(configChild.map(child -> child == null ? null : child.observeValue()), null);
			theConfigChild = configChild;
		}

		@Override
		protected Setter<String> createSetter(Getter<? extends ObservableValue<? extends String>> outer,
			ObservableValue<? extends String> wrapped, Setter<? extends String> setter, Object cause) {
			return new ConfigValueSetter(outer, wrapped, setter, cause);
		}

		protected class ConfigValueSetter extends FlattenedValueSetter {
			private final Getter<ObservableConfig> theConfigGetter;

			protected ConfigValueSetter(Getter<? extends ObservableValue<? extends String>> outerGetter,
				ObservableValue<? extends String> innerValue, Setter<? extends String> innerGetter, Object cause) {
				super(outerGetter, innerValue, innerGetter, cause);
				theConfigGetter = theConfigChild.lock(false);
			}

			@Override
			public String isEnabled() {
				String msg = theConfigChild.canResolvePath(0, true);
				if (msg == null) {
					ObservableConfig child = theConfigGetter.get();
					if (child == null)
						msg = StdMsg.UNSUPPORTED_OPERATION;
					if (msg == null)
						msg = child.canSetValue(child.getValue());
				}
				return msg;
			}

			@Override
			public String isAcceptable(String value) {
				String msg = isEnabled();
				if (msg == null)
					msg = isValueAcceptable(value);
				return msg;
			}

			@Override
			public String set(String value) {
				String msg = isValueAcceptable(value);
				if (msg != null)
					throw new IllegalArgumentException(msg);
				theConfigChild.resolvePath(0, true);
				ObservableConfig child = theConfigGetter.get();
				String oldValue = child.getValue();
				child.setValue(value);
				return oldValue;
			}

			@Override
			public void close() {
				theConfigGetter.close();
				super.close();
			}
		}

		@Override
		public Collection<Cause> getCurrentCauses() {
			ObservableConfig config = theConfigChild.get();
			return config == null ? Collections.emptyList() : config.getCurrentCauses();
		}

		@Override
		protected Object createIdentity() {
			return Identifiable.wrap(theConfigChild.getIdentity(), "value");
		}

		@Override
		public String set(String value) throws IllegalArgumentException, UnsupportedOperationException {
			try (Transaction t = theConfigChild.getRoot().lockWrite(false, null)) {
				String msg = isValueAcceptable(value);
				if (msg != null)
					throw new IllegalArgumentException(msg);
				theConfigChild.resolvePath(0, true);
				ObservableConfig child = theConfigChild.get();
				String oldValue = child == null ? null : child.getValue();
				child.setValue(value);
				return oldValue;
			}
		}

		/**
		 * @param value The value to set
		 * @return null if the given value is acceptable for the target config's value, or a reason why it isn't
		 */
		protected String isValueAcceptable(String value) {
			return null;
		}

		@Override
		public String isAcceptable(String value) {
			String msg = isEnabled().get();
			if (msg == null)
				msg = isValueAcceptable(value);
			return msg;
		}

		@Override
		public ObservableValue<String> isEnabled() {
			// We'll assume this doesn't change
			try (Transaction t = theConfigChild.getRoot().lock(false)) {
				String msg = theConfigChild.canResolvePath(0, true);
				if (msg == null) {
					ObservableConfig child = theConfigChild.get();
					if (child == null)
						msg = StdMsg.UNSUPPORTED_OPERATION;
					if (msg == null)
						msg = theConfigChild.get().canSetValue(theConfigChild.get().getValue());
				}
				if (msg != null)
					return ObservableValue.of(msg);
			}
			return SettableValue.ALWAYS_ENABLED;
		}
	}

	/** Superclass to assist in implementing the collection behind {@link ObservableConfig#getContent(ObservableConfigPath)} */
	protected static abstract class AbstractObservableConfigContent extends AbstractIdentifiable
	implements ObservableCollection<ObservableConfig> {
		private final ObservableConfig theConfig;

		/** @param config The root config */
		protected AbstractObservableConfigContent(ObservableConfig config) {
			theConfig = config;
		}

		/** @return The root config */
		public ObservableConfig getConfig() {
			return theConfig;
		}

		@Override
		public AbstractObservableConfigContent alias(String alias) {
			super.alias(alias);
			return this;
		}

		@Override
		public ThreadConstraint getThreadConstraint() {
			return theConfig.getThreadConstraint();
		}

		@Override
		public boolean isEventing() {
			return theConfig.isEventing();
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
		public Transaction lock(boolean tryOnly) {
			return theConfig.lock(tryOnly);
		}

		@Override
		public Transaction lockWrite(boolean tryOnly, Object cause) {
			return theConfig.lockWrite(tryOnly, cause);
		}

		@Override
		public Collection<Cause> getCurrentCauses() {
			return theConfig.getCurrentCauses();
		}

		@Override
		public CoreId getCoreId() {
			return theConfig.getCoreId();
		}

		@Override
		public CoreChangeSources getChangeSources() {
			return theConfig.getChangeSources();
		}

		@Override
		public Equivalence<? super ObservableConfig> equivalence() {
			return Equivalence.DEFAULT;
		}
	}

	/** Implements the collection behind {@link ObservableConfig#getAllContent()} */
	protected static class FullObservableConfigContent extends AbstractObservableConfigContent {
		/** @param config The parent config */
		public FullObservableConfigContent(ObservableConfig config) {
			super(config);
		}

		@Override
		protected Object createIdentity() {
			return Identifiable.wrap(getConfig(), "allContent");
		}

		@Override
		public void clear() {
			try (Transaction t = getConfig().lockWrite(false, null)) {
				ObservableConfig lastChild = getConfig().getContent().peekLast();
				while (lastChild != null) {
					ObservableConfig nextLast = getConfig().getSibling(false);
					lastChild.remove();
					lastChild = nextLast;
				}
			}
		}

		@Override
		public int size() {
			return getConfig().getContent().size();
		}

		@Override
		public boolean isEmpty() {
			return getConfig().getContent().isEmpty();
		}

		@Override
		public ListElement<ObservableConfig> getElement(int index) {
			ObservableConfig child = getConfig().getContent().get(index);
			return new ConfigCollectionElement(child);
		}

		@Override
		public ListElement<ObservableConfig> getElement(ObservableConfig value, boolean first) {
			ObservableConfig config = CollectionElement.get(getConfig().getContent().getElement(value, first));
			return config == null ? null : new ConfigCollectionElement(config);
		}

		@Override
		public ListElement<ObservableConfig> getElement(ElementId id) {
			ObservableConfig config = CollectionElement.get(getConfig().getContent().getElement(id));
			return new ConfigCollectionElement(config);
		}

		@Override
		public ListElement<ObservableConfig> getTerminalElement(boolean first) {
			ObservableConfig config = CollectionElement.get(getConfig().getContent().getTerminalElement(first));
			return config == null ? null : new ConfigCollectionElement(config);
		}

		@Override
		public MutableListElement<ObservableConfig> mutableElement(ElementId id) {
			ObservableConfig config = CollectionElement.get(getConfig().getContent().getElement(id));
			return new MutableConfigCollectionElement(config);
		}

		@Override
		public BetterList<CollectionElement<ObservableConfig>> getElementsBySource(ElementId sourceEl,
			BetterCollection<?> sourceCollection) {
			if (sourceCollection == this)
				return BetterList.of(getElement(sourceEl));
			return QommonsUtils.map2(getConfig().getContent().getElementsBySource(sourceEl, sourceCollection),
				el -> new ConfigCollectionElement(el.get()));
		}

		@Override
		public BetterList<ElementId> getSourceElements(ElementId localElement, BetterCollection<?> sourceCollection) {
			if (sourceCollection == this)
				return getConfig().getContent().getSourceElements(localElement, getConfig().getContent());
			return getConfig().getContent().getSourceElements(localElement, sourceCollection);
		}

		@Override
		public ElementId getEquivalentElement(ElementId equivalentEl) {
			return getConfig().getContent().getEquivalentElement(equivalentEl);
		}

		@Override
		public String canAdd(ObservableConfig value, ElementId after, ElementId before) {
			return StdMsg.UNSUPPORTED_OPERATION;
		}

		@Override
		public ListElement<ObservableConfig> addElement(ObservableConfig value, ElementId after, ElementId before, boolean first)
			throws UnsupportedOperationException, IllegalArgumentException {
			throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
		}

		@Override
		public String canMove(ElementId valueEl, ElementId after, ElementId before) {
			return getConfig().canMoveChild(//
				getConfig().getContent().getElement(valueEl).get(), //
				after == null ? null : getConfig().getContent().getElement(after).get(), //
					before == null ? null : getConfig().getContent().getElement(before).get());
		}

		@Override
		public ListElement<ObservableConfig> move(ElementId valueEl, ElementId after, ElementId before, boolean first, Runnable afterRemove)
			throws UnsupportedOperationException, IllegalArgumentException {
			ObservableConfig valueConfig = getConfig().getContent().getElement(valueEl).get();
			ObservableConfig afterConfig = after == null ? null : getConfig().getContent().getElement(after).get();
			ObservableConfig beforeConfig = before == null ? null : getConfig().getContent().getElement(before).get();
			return new ConfigCollectionElement(getConfig()//
				.moveChild(valueConfig, afterConfig, beforeConfig, first, afterRemove));
		}

		@Override
		public void setValue(Collection<ElementId> elements, ObservableConfig value) {
			throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
		}

		@Override
		public Subscription onChange(Consumer<? super ObservableCollectionEvent<? extends ObservableConfig>> observer) {
			return getConfig().watch(ObservableConfigPath.create(ObservableConfigPath.ANY_NAME)).act(evt -> {
				ObservableConfig child = evt.relativePath.get(0);
				ObservableConfig oldValue = evt.changeType == CollectionChangeType.add ? null : child;
				ObservableCollectionEvent<ObservableConfig> collEvt = ObservableCollectionEvent.createCollectionEvent(
					child.getParentChildRef().getElementId(), child.getIndexInParent(), evt.changeType, oldValue, child, evt, evt.movement);
				observer.accept(collEvt);
			});
		}
	}

	private static class ConfigCollectionElement implements ListElement<ObservableConfig> {
		final ObservableConfig theConfig;

		ConfigCollectionElement(ObservableConfig config) {
			theConfig = config;
		}

		@Override
		public ElementId getElementId() {
			return theConfig.getParentChildRef().getElementId();
		}

		@Override
		public ObservableConfig get() {
			return theConfig;
		}

		@Override
		public ListElement<ObservableConfig> getAdjacent(boolean next) {
			ObservableConfig adj = theConfig.getSibling(next);
			return adj == null ? null : new ConfigCollectionElement(adj);
		}

		@Override
		public int getElementsBefore() {
			return theConfig.getIndexInParent();
		}

		@Override
		public int getElementsAfter() {
			return theConfig.getIndexInParent();
		}

		@Override
		public int hashCode() {
			return theConfig.getParentChildRef().hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			return obj instanceof ConfigCollectionElement && ((ConfigCollectionElement) obj).theConfig == theConfig;
		}

		@Override
		public String toString() {
			return theConfig.toString();
		}
	}

	private static class MutableConfigCollectionElement extends ConfigCollectionElement implements MutableListElement<ObservableConfig> {
		MutableConfigCollectionElement(ObservableConfig config) {
			super(config);
		}

		@Override
		public MutableListElement<ObservableConfig> getAdjacent(boolean next) {
			ObservableConfig adj = theConfig.getSibling(next);
			return adj == null ? null : new MutableConfigCollectionElement(adj);
		}

		@Override
		public String isEnabled() {
			return StdMsg.UNSUPPORTED_OPERATION;
		}

		@Override
		public String isAcceptable(ObservableConfig value) {
			return StdMsg.UNSUPPORTED_OPERATION;
		}

		@Override
		public void set(ObservableConfig value) throws UnsupportedOperationException, IllegalArgumentException {
			if (value == get()) {
				value.setValue(value.getValue());
			} else
				throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
		}

		@Override
		public String canRemove() {
			return theConfig.canRemove();
		}

		@Override
		public void remove() throws UnsupportedOperationException {
			theConfig.remove();
		}
	}

	/** Implements the collection behind {@link ObservableConfig#getContent(ObservableConfigPath)} for single-element paths */
	protected static class SimpleObservableConfigContent extends AbstractObservableConfigContent {
		private final ObservableConfigPathElement thePathElement;

		/**
		 * @param config The parent config
		 * @param pathEl The path element
		 */
		public SimpleObservableConfigContent(ObservableConfig config, ObservableConfigPathElement pathEl) {
			super(config);
			thePathElement = pathEl;
		}

		@Override
		protected Object createIdentity() {
			return Identifiable.wrap(getConfig(), "content", thePathElement);
		}

		@Override
		public int size() {
			try (Transaction t = getConfig().lock(false)) {
				return (int) getConfig().getContent().stream().filter(thePathElement::matches).count();
			}
		}

		@Override
		public boolean isEmpty() {
			try (Transaction t = getConfig().lock(false)) {
				return getConfig().getContent().stream().anyMatch(thePathElement::matches);
			}
		}

		@Override
		public ListElement<ObservableConfig> getElement(int index) {
			try (Transaction t = getConfig().lock(false)) {
				int i = 0;
				for (CollectionElement<ObservableConfig> el : getConfig().getContent().elements()) {
					if (thePathElement.matches(el.get())) {
						if (i == index)
							return new ConfigCollectionElement(el.get());
						i++;
					}
				}
				throw new IndexOutOfBoundsException(index + " of " + i);
			}
		}

		@Override
		public ListElement<ObservableConfig> getElement(ObservableConfig value, boolean first) {
			if (!thePathElement.matches(value))
				return null;
			try (Transaction t = getConfig().lock(false)) {
				ObservableConfig config = CollectionElement.get(getConfig().getContent().getElement(value, first));
				return config == null ? null : new ConfigCollectionElement(config);
			}
		}

		@Override
		public ListElement<ObservableConfig> getElement(ElementId id) {
			try (Transaction t = getConfig().lock(false)) {
				ObservableConfig config = CollectionElement.get(getConfig().getContent().getElement(id));
				if (!thePathElement.matches(config))
					throw new NoSuchElementException();
				return new ConfigCollectionElement(config);
			}
		}

		@Override
		public BetterList<CollectionElement<ObservableConfig>> getElementsBySource(ElementId sourceEl,
			BetterCollection<?> sourceCollection) {
			if (sourceCollection == this)
				return BetterList.of(getElement(sourceEl));
			return QommonsUtils.filterMap(getConfig().getContent().getElementsBySource(sourceEl, sourceCollection),
				el -> thePathElement.matches(el.get()), el -> new ConfigCollectionElement(el.get()));
		}

		@Override
		public BetterList<ElementId> getSourceElements(ElementId localElement, BetterCollection<?> sourceCollection) {
			if (sourceCollection == this)
				return getConfig().getContent().getSourceElements(localElement, getConfig().getContent());
			return getConfig().getContent().getSourceElements(localElement, sourceCollection);
		}

		@Override
		public ElementId getEquivalentElement(ElementId equivalentEl) {
			ElementId found = getConfig().getContent().getEquivalentElement(equivalentEl);
			if (found == null)
				return null;
			ObservableConfig config = getConfig().getContent().getElement(found).get();
			if (!thePathElement.matches(config))
				return null;
			return equivalentEl;
		}

		@Override
		public ListElement<ObservableConfig> getTerminalElement(boolean first) {
			try (Transaction t = getConfig().lock(false)) {
				ObservableConfig config = CollectionElement.get(getConfig().getContent().getTerminalElement(first));
				while (config != null && !thePathElement.matches(config))
					config = config.getSibling(first);
				return config == null ? null : new ConfigCollectionElement(config);
			}
		}

		@Override
		public MutableListElement<ObservableConfig> mutableElement(ElementId id) {
			try (Transaction t = getConfig().lock(false)) {
				ObservableConfig config = CollectionElement.get(getConfig().getContent().getElement(id));
				if (!thePathElement.matches(config))
					throw new NoSuchElementException();
				return new MutableConfigCollectionElement(config);
			}
		}

		@Override
		public String canAdd(ObservableConfig value, ElementId after, ElementId before) {
			return StdMsg.UNSUPPORTED_OPERATION;
		}

		@Override
		public ListElement<ObservableConfig> addElement(ObservableConfig value, ElementId after, ElementId before, boolean first)
			throws UnsupportedOperationException, IllegalArgumentException {
			throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
		}

		@Override
		public String canMove(ElementId valueEl, ElementId after, ElementId before) {
			return getConfig().canMoveChild(//
				getConfig().getContent().getElement(valueEl).get(), //
				after == null ? null : getConfig().getContent().getElement(after).get(), //
					before == null ? null : getConfig().getContent().getElement(before).get());
		}

		@Override
		public ListElement<ObservableConfig> move(ElementId valueEl, ElementId after, ElementId before, boolean first, Runnable afterRemove)
			throws UnsupportedOperationException, IllegalArgumentException {
			ObservableConfig valueConfig = getConfig().getContent().getElement(valueEl).get();
			ObservableConfig afterConfig = after == null ? null : getConfig().getContent().getElement(after).get();
			ObservableConfig beforeConfig = before == null ? null : getConfig().getContent().getElement(before).get();
			return new ConfigCollectionElement(getConfig()//
				.moveChild(valueConfig, afterConfig, beforeConfig, first, afterRemove));
		}

		@Override
		public void setValue(Collection<ElementId> elements, ObservableConfig value) {
			throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
		}

		@Override
		public void clear() {
			try (Transaction t = getConfig().lockWrite(false, null)) {
				for (CollectionElement<ObservableConfig> el : getConfig().getContent().elements()) {
					if (thePathElement.matches(el.get()))
						el.get().remove();
				}
			}
		}

		@Override
		public Subscription onChange(Consumer<? super ObservableCollectionEvent<? extends ObservableConfig>> observer) {
			String watchPath = thePathElement.getName() + ObservableConfigPath.PATH_SEPARATOR + ObservableConfigPath.ANY_DEPTH;
			return getConfig().watch(ObservableConfigPath.create(watchPath)).act(evt -> {
				ObservableConfig child = evt.relativePath.get(0);
				boolean postMatches = thePathElement.matches(child);
				boolean preMatches = evt.changeType == CollectionChangeType.set ? thePathElement.matchedBefore(child, evt) : postMatches;
				if (preMatches || postMatches) {
					int index;
					if (postMatches)
						index = child.getIndexInParent();
					else {
						int i = 0;
						for (CollectionElement<ObservableConfig> el : getConfig().getContent().elements()) {
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

					ObservableConfig oldValue = changeType == CollectionChangeType.add ? null : child;
					ObservableCollectionEvent<ObservableConfig> collEvt = ObservableCollectionEvent.createCollectionEvent(
						child.getParentChildRef().getElementId(), index, changeType, oldValue, child, evt,
						preMatches ? evt.movement : null);
					try (Transaction t = collEvt.use()) {
						observer.accept(collEvt);
					}
				}
			});
		}
	}

	/** Implements the value set portion of {@link ObservableConfig#getContent(ObservableConfigPath)} */
	protected static class ObservableChildSet implements SyncValueSet<ObservableConfig> {
		private final ObservableConfig theRoot;
		private final ObservableConfigPath thePath;
		private final ObservableCollection<ObservableConfig> theChildren;

		/**
		 * @param root The root config
		 * @param path The path for the values
		 * @param children The child collection
		 */
		public ObservableChildSet(ObservableConfig root, ObservableConfigPath path, ObservableCollection<ObservableConfig> children) {
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
		public ConfiguredValueType<ObservableConfig> getType() {
			return new ConfiguredValueType<ObservableConfig>() {
				@Override
				public TypeToken<ObservableConfig> getType() {
					return TypeTokens.get().of(ObservableConfig.class);
				}

				@Override
				public List<? extends ConfiguredValueType<? super ObservableConfig>> getSupers() {
					return Collections.emptyList();
				}

				@Override
				public QuickMap<String, ? extends ConfiguredValueField<ObservableConfig, ?>> getFields() {
					return QuickSet.<String> empty().createMap();
				}

				@Override
				public <F> ConfiguredValueField<ObservableConfig, F> getField(Function<? super ObservableConfig, F> fieldGetter) {
					throw new UnsupportedOperationException("No typed fields for an " + ObservableConfig.class.getSimpleName());
				}

				@Override
				public boolean allowsCustomFields() {
					return true;
				}
			};
		}

		@Override
		public ObservableCollection<ObservableConfig> getValues() {
			return theChildren;
		}

		@Override
		public <C2 extends ObservableConfig> SyncValueCreator<ObservableConfig, C2> create(TypeToken<C2> subType) {
			if (subType != getType().getType())
				throw new IllegalArgumentException("Unrecognized " + ObservableConfig.class.getSimpleName() + " sub-type " + subType);
			return (SyncValueCreator<ObservableConfig, C2>) new SyncValueCreator<ObservableConfig, ObservableConfig>() {
				private Map<String, String> theFields;
				private ElementId theAfter;
				private ElementId theBefore;
				private boolean isTowardBeginning;

				@Override
				public ConfiguredValueType<ObservableConfig> getType() {
					return ObservableChildSet.this.getType();
				}

				@Override
				public Set<Integer> getRequiredFields() {
					return Collections.emptySet();
				}

				@Override
				public SyncValueCreator<ObservableConfig, ObservableConfig> after(ElementId after) {
					theAfter = after;
					return this;
				}

				@Override
				public SyncValueCreator<ObservableConfig, ObservableConfig> before(ElementId before) {
					theBefore = before;
					return this;

				}

				@Override
				public SyncValueCreator<ObservableConfig, ObservableConfig> towardBeginning(boolean towardBeginning) {
					isTowardBeginning = towardBeginning;
					return this;
				}

				@Override
				public String isEnabled(ConfiguredValueField<? super ObservableConfig, ?> field) {
					return null;
				}

				@Override
				public <F> String isAcceptable(ConfiguredValueField<? super ObservableConfig, F> field, F value) {
					return null;
				}

				@Override
				public SyncValueCreator<ObservableConfig, ObservableConfig> with(String fieldName, Object value)
					throws IllegalArgumentException {
					if (theFields == null)
						theFields = new LinkedHashMap<>();
					theFields.put(fieldName, String.valueOf(value));
					return this;
				}

				@Override
				public <F> SyncValueCreator<ObservableConfig, ObservableConfig> with(ConfiguredValueField<ObservableConfig, F> field,
					F value) throws IllegalArgumentException {
					throw new UnsupportedOperationException();
				}

				@Override
				public <F> SyncValueCreator<ObservableConfig, ObservableConfig> with(Function<? super ObservableConfig, F> field, F value)
					throws IllegalArgumentException {
					throw new UnsupportedOperationException();
				}

				@Override
				public String canCreate() {
					ObservableConfig afterChild = theAfter == null ? null : theChildren.getElement(theAfter).get();
					ObservableConfig beforeChild = theBefore == null ? null : theChildren.getElement(theBefore).get();
					try (Transaction t = theRoot.lockWrite(false, null)) {
						ObservableConfig parent = thePath.getParent() == null ? theRoot : theRoot.getChild(thePath.getParent(), true, null);
						return parent.canAddChild(afterChild, beforeChild);
					}
				}

				@Override
				public CollectionElement<ObservableConfig> create(Consumer<? super ObservableConfig> preAddAction) {
					ObservableConfig afterChild = theAfter == null ? null : theChildren.getElement(theAfter).get();
					ObservableConfig beforeChild = theBefore == null ? null : theChildren.getElement(theBefore).get();
					ElementId newChildId;
					try (Transaction t = theRoot.lockWrite(false, null)) {
						ObservableConfig parent = thePath.getParent() == null ? theRoot : theRoot.getChild(thePath.getParent(), true, null);
						ObservableConfig newChild = parent.addChild(afterChild, beforeChild, isTowardBeginning,
							thePath.getLastElement().getName(), cfg -> {
								if (theFields != null)
									for (Map.Entry<String, String> field : theFields.entrySet())
										cfg.set(field.getKey(), field.getValue());
								if (preAddAction != null)
									preAddAction.accept(cfg);
							});
						newChildId = theChildren.getElementsBySource(newChild.getParentChildRef().getElementId(), parent.getContent())
							.getFirst().getElementId();
					}
					return theChildren.getElement(newChildId);
				}
			};
		}

		static void copy(ObservableConfig source, ObservableConfig dest) {
			dest.setValue(source.getValue());
			for (int i = 0; i < source.getContent().size(); i++) {
				ObservableConfig srcChild = source.getContent().get(i);
				ObservableConfig destChild = dest.addChild(srcChild.getName());
				copy(srcChild, destChild);
			}
		}
	}
}
