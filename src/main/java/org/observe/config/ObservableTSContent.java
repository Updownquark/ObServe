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
import org.observe.ObservableValue;
import org.observe.ObservableValueEvent;
import org.observe.Observer;
import org.observe.SettableValue;
import org.observe.Subscription;
import org.observe.collect.CollectionChangeType;
import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableCollectionEvent;
import org.observe.config.ObservableTextStructure.ObservableStructureEvent;
import org.observe.util.TypeTokens;
import org.qommons.Identifiable;
import org.qommons.Identifiable.AbstractIdentifiable;
import org.qommons.Lockable;
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

/** Contains implementation classes for {@link ObservableTextStructure} methods */
public class ObservableTSContent {
	/** Observes a config's descendant at a path */
	protected static class ObservableTSChild extends AbstractIdentifiable implements ObservableValue<ObservableTextStructure> {
		private final ObservableTextStructure theRoot;
		private final ObservableTextPath thePath;
		private final ObservableTextStructure[] thePathElements;
		private final long[] thePathElementStamps;
		private Subscription thePathSubscription;
		private final ListenerList<Observer<? super ObservableValueEvent<ObservableTextStructure>>> theListeners;
		private Object theChangesIdentity;

		/**
		 * @param root The root config to observe
		 * @param path The path of the descendant to observe
		 */
		public ObservableTSChild(ObservableTextStructure root, ObservableTextPath path) {
			theRoot = root;
			thePath = path;
			for (ObservableTextPath.ObservableTextPathElement el : path.getElements()) {
				if (el.isMulti())
					throw new IllegalArgumentException("Cannot use observeValue with a variable path");
			}
			thePathElements = new ObservableTextStructure[path.getElements().size()];
			thePathElementStamps = new long[path.getElements().size()];
			theListeners = ListenerList.build().withInUse(this::setInUse).build();
		}

		ObservableTextStructure getRoot() {
			return theRoot;
		}

		void setInUse(boolean inUse) {
			try (Transaction t = theRoot.lock(false, null)) {
				if (inUse) {
					thePathSubscription = theRoot.watch(ObservableTextPath.ANY_DEPTH).act(evt -> {
						int size = evt.relativePath.size();
						if (size > thePathElements.length)
							return;
						int pathIndex = 0;
						ObservableStructureEvent pathChange = evt;
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
					});
				} else {
					thePathSubscription.unsubscribe();
					thePathSubscription = null;
				}
			}
		}

		void handleChange(int pathIndex, ObservableStructureEvent pathChange, ObservableStructureEvent evt) {
			boolean checkForChange = false;
			switch (evt.changeType) {
			case add:
				if (pathIndex >= thePathElements.length
				|| !thePath.getElements().get(pathIndex).matchedBefore(pathChange.eventTarget, pathChange)) {
					checkForChange = true;
				} else if (thePathElements[pathIndex] == null
					|| pathChange.eventTarget.getParentChildRef().compareTo(thePathElements[pathIndex].getParentChildRef()) < 0) {
					ObservableTextStructure oldValue = thePathElements[thePathElements.length - 1];
					thePathElements[pathIndex] = evt.relativePath.get(pathIndex);
					thePathElementStamps[pathIndex] = -1;
					if (pathIndex < thePathElements.length - 1)
						resolvePath(pathIndex + 1, false);
					ObservableTextStructure newValue = thePathElements[thePathElements.length - 1];
					fire(createChangeEvent(oldValue, newValue, evt));
				}
				break;
			case remove:
				if (evt.relativePath.isEmpty())
					return; // Removed the root, but we'll keep listening to it
				if (pathIndex == evt.relativePath.size()) {
					pathIndex--;
					ObservableTextStructure oldValue = thePathElements[thePathElements.length - 1];
					Arrays.fill(thePathElements, pathIndex, thePathElements.length, null);
					thePathElementStamps[pathIndex] = -1;
					resolvePath(pathIndex, false);
					ObservableTextStructure newValue = thePathElements[thePathElements.length - 1];
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
					ObservableTextStructure oldValue = thePathElements[thePathElements.length - 1];
					Arrays.fill(thePathElements, pathIndex, thePathElements.length, null);
					resolvePath(pathIndex, false);
					ObservableTextStructure newValue = thePathElements[thePathElements.length - 1];
					if (oldValue != newValue)
						fire(createChangeEvent(oldValue, newValue, evt));
				} else
					checkForChange = true;
				break;
			}
			if (checkForChange) {
				ObservableTextStructure oldValue = thePathElements[thePathElements.length - 1];
				resolvePath(pathIndex, false);
				ObservableTextStructure newValue = thePathElements[thePathElements.length - 1];
				if (oldValue != newValue)
					fire(createChangeEvent(oldValue, newValue, evt));
			}
		}

		private void fire(ObservableValueEvent<ObservableTextStructure> event) {
			try (Transaction t = event.use()) {
				theListeners.forEach(//
					listener -> listener.onNext(event));
			}
		}

		@Override
		public TypeToken<ObservableTextStructure> getType() {
			return TypeTokens.get().of(ObservableTextStructure.class);
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
		public ObservableTextStructure get() {
			try (Transaction t = lock()) {
				resolvePath(0, false);
				return thePathElements[thePathElements.length - 1];
			}
		}

		boolean resolvePath(int startIndex, boolean createIfAbsent) {
			ObservableTextStructure parent = startIndex == 0 ? theRoot : thePathElements[startIndex - 1];
			boolean resolved = true;
			boolean changed = false;
			int i;
			for (i = startIndex; i < thePathElements.length; i++) {
				long stamp = parent.getStamp();
				ObservableTextStructure child;
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
		public Observable<ObservableValueEvent<ObservableTextStructure>> noInitChanges() {
			return new Observable<ObservableValueEvent<ObservableTextStructure>>() {
				@Override
				public Object getIdentity() {
					if (theChangesIdentity == null)
						theChangesIdentity = Identifiable.wrap(ObservableTSChild.this.getIdentity(), "noInitChanges");
					return theChangesIdentity;
				}

				@Override
				public Subscription subscribe(Observer<? super ObservableValueEvent<ObservableTextStructure>> observer) {
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
	protected static class ObservableTSValue extends AbstractIdentifiable implements SettableValue<String> {
		private final ObservableTSChild theConfigChild;
		private Object theChangesIdentity;

		/**
		 * @param root The root config to observe
		 * @param path The path of the config's descendant to observe the value of
		 */
		public ObservableTSValue(ObservableTextStructure root, ObservableTextPath path) {
			theConfigChild = new ObservableTSChild(root, path);
		}

		@Override
		public TypeToken<String> getType() {
			return TypeTokens.get().STRING;
		}

		@Override
		public boolean isLockSupported() {
			if (!theConfigChild.isLockSupported())
				return false;
			ObservableTextStructure child = theConfigChild.get();
			return child.isLockSupported();
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return Lockable.lock(theConfigChild, () -> Lockable.lockable(theConfigChild.get(), write, cause));
		}

		@Override
		public Transaction tryLock(boolean write, Object cause) {
			return Lockable.tryLock(theConfigChild, () -> Lockable.lockable(theConfigChild.get(), write, cause));
		}

		@Override
		public long getStamp() {
			long stamp = theConfigChild.getStamp();
			ObservableTextStructure child = theConfigChild.get();
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

		private String parse(ObservableTextStructure config) {
			return config == null ? null : config.getValue();
		}

		@Override
		public Observable<ObservableValueEvent<String>> noInitChanges() {
			class Changes extends AbstractIdentifiable implements Observable<ObservableValueEvent<String>> {
				@Override
				protected Object createIdentity() {
					if(theChangesIdentity==null)
						theChangesIdentity = Identifiable.wrap(ObservableTSValue.this.getIdentity(), "noInitChanges");
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
							ObservableTextStructure newConfig = evt.getNewValue();
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

	/** Superclass to assist in implementing the collection behind {@link ObservableTextStructure#getContent(ObservableTextPath)} */
	protected static abstract class AbstractObservableTSContent implements ObservableCollection<ObservableTextStructure> {
		private final ObservableTextStructure theConfig;

		/** @param config The root config */
		public AbstractObservableTSContent(ObservableTextStructure config) {
			theConfig = config;
		}

		/** @return The root config */
		public ObservableTextStructure getConfig() {
			return theConfig;
		}

		@Override
		public TypeToken<ObservableTextStructure> getType() {
			return TypeTokens.get().of(ObservableTextStructure.class);
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
		public Equivalence<? super ObservableTextStructure> equivalence() {
			return Equivalence.DEFAULT;
		}
	}

	/** Implements the collection behind {@link ObservableTextStructure#getAllContent()} */
	protected static class FullObservableTSContent extends AbstractObservableTSContent {
		private Object theIdentity;

		/** @param config The parent config */
		public FullObservableTSContent(ObservableTextStructure config) {
			super(config);
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
				ObservableTextStructure lastChild = getConfig().getContent().peekLast();
				while (lastChild != null) {
					ObservableTextStructure nextLast = CollectionElement
						.get(getConfig().getContent().getAdjacentElement(lastChild.getParentChildRef(), false));
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
		public CollectionElement<ObservableTextStructure> getElement(int index) {
			ObservableTextStructure child = getConfig().getContent().get(index);
			return new TSCollectionElement(child);
		}

		@Override
		public int getElementsBefore(ElementId id) {
			return getConfig().getContent().getElementsBefore(id);
		}

		@Override
		public int getElementsAfter(ElementId id) {
			return getConfig().getContent().getElementsAfter(id);
		}

		@Override
		public CollectionElement<ObservableTextStructure> getElement(ObservableTextStructure value, boolean first) {
			ObservableTextStructure config = CollectionElement.get(getConfig().getContent().getElement(value, first));
			return config == null ? null : new TSCollectionElement(config);
		}

		@Override
		public CollectionElement<ObservableTextStructure> getElement(ElementId id) {
			ObservableTextStructure config = CollectionElement.get(getConfig().getContent().getElement(id));
			return new TSCollectionElement(config);
		}

		@Override
		public CollectionElement<ObservableTextStructure> getTerminalElement(boolean first) {
			ObservableTextStructure config = CollectionElement.get(getConfig().getContent().getTerminalElement(first));
			return config == null ? null : new TSCollectionElement(config);
		}

		@Override
		public CollectionElement<ObservableTextStructure> getAdjacentElement(ElementId elementId, boolean next) {
			ObservableTextStructure config = CollectionElement.get(getConfig().getContent().getAdjacentElement(elementId, next));
			return config == null ? null : new TSCollectionElement(config);
		}

		@Override
		public MutableCollectionElement<ObservableTextStructure> mutableElement(ElementId id) {
			ObservableTextStructure config = CollectionElement.get(getConfig().getContent().getElement(id));
			return new MutableTSCollectionElement(config, this);
		}

		@Override
		public BetterList<CollectionElement<ObservableTextStructure>> getElementsBySource(ElementId sourceEl,
			BetterCollection<?> sourceCollection) {
			if (sourceCollection == this)
				return BetterList.of(getElement(sourceEl));
			return QommonsUtils.map2(getConfig().getContent().getElementsBySource(sourceEl, sourceCollection),
				el -> new TSCollectionElement(el.get()));
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
		public String canAdd(ObservableTextStructure value, ElementId after, ElementId before) {
			return StdMsg.UNSUPPORTED_OPERATION;
		}

		@Override
		public CollectionElement<ObservableTextStructure> addElement(ObservableTextStructure value, ElementId after, ElementId before,
			boolean first)
				throws UnsupportedOperationException, IllegalArgumentException {
			throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
		}

		@Override
		public String canMove(ElementId valueEl, ElementId after, ElementId before) {
			return null;
		}

		@Override
		public CollectionElement<ObservableTextStructure> move(ElementId valueEl, ElementId after, ElementId before, boolean first,
			Runnable afterRemove)
				throws UnsupportedOperationException, IllegalArgumentException {
			ObservableTextStructure valueConfig = getConfig().getContent().getElement(valueEl).get();
			ObservableTextStructure afterConfig = after == null ? null : getConfig().getContent().getElement(after).get();
			ObservableTextStructure beforeConfig = before == null ? null : getConfig().getContent().getElement(before).get();
			return new TSCollectionElement(getConfig()//
				.moveChild(valueConfig, afterConfig, beforeConfig, first, afterRemove));
		}

		@Override
		public void setValue(Collection<ElementId> elements, ObservableTextStructure value) {
			throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
		}

		@Override
		public Subscription onChange(Consumer<? super ObservableCollectionEvent<? extends ObservableTextStructure>> observer) {
			return getConfig().watch(ObservableTextPath.createPath(ObservableTextPath.ANY_NAME)).act(evt -> {
				ObservableTextStructure child = evt.relativePath.get(0);
				ObservableTextStructure oldValue = evt.changeType == CollectionChangeType.add ? null : child;
				ObservableCollectionEvent<ObservableTextStructure> collEvt = new ObservableCollectionEvent<>(child.getParentChildRef(),
					getType(), child.getIndexInParent(), evt.changeType, evt.isMove, oldValue, child, evt);
				observer.accept(collEvt);
			});
		}
	}

	private static class TSCollectionElement implements CollectionElement<ObservableTextStructure> {
		final ObservableTextStructure theConfig;

		TSCollectionElement(ObservableTextStructure config) {
			theConfig = config;
		}

		@Override
		public ElementId getElementId() {
			return theConfig.getParentChildRef();
		}

		@Override
		public ObservableTextStructure get() {
			return theConfig;
		}

		@Override
		public int hashCode() {
			return theConfig.getParentChildRef().hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			return obj instanceof TSCollectionElement && ((TSCollectionElement) obj).theConfig == theConfig;
		}

		@Override
		public String toString() {
			return theConfig.toString();
		}
	}

	private static class MutableTSCollectionElement extends TSCollectionElement
	implements MutableCollectionElement<ObservableTextStructure> {
		private final AbstractObservableTSContent theCollection;

		MutableTSCollectionElement(ObservableTextStructure config, AbstractObservableTSContent collection) {
			super(config);
			theCollection = collection;
		}

		@Override
		public BetterCollection<ObservableTextStructure> getCollection() {
			return theCollection;
		}

		@Override
		public String isEnabled() {
			return StdMsg.UNSUPPORTED_OPERATION;
		}

		@Override
		public String isAcceptable(ObservableTextStructure value) {
			return StdMsg.UNSUPPORTED_OPERATION;
		}

		@Override
		public void set(ObservableTextStructure value) throws UnsupportedOperationException, IllegalArgumentException {
			if (value == get())
				value.setValue(value.getValue());
			else
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

	/** Implements the collection behind {@link ObservableTextStructure#getContent(ObservableTextPath)} for single-element paths */
	protected static class SimpleObservableTSContent extends AbstractObservableTSContent {
		private final ObservableTextPath.ObservableTextPathElement thePathElement;
		private Object theIdentity;

		/**
		 * @param config The parent config
		 * @param pathEl The path element
		 */
		public SimpleObservableTSContent(ObservableTextStructure config, ObservableTextPath.ObservableTextPathElement pathEl) {
			super(config);
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
				return (int) getConfig().getContent().stream().filter(thePathElement::matches).count();
			}
		}

		@Override
		public boolean isEmpty() {
			try (Transaction t = getConfig().lock(false, null)) {
				return getConfig().getContent().stream().anyMatch(thePathElement::matches);
			}
		}

		@Override
		public CollectionElement<ObservableTextStructure> getElement(int index) {
			try (Transaction t = getConfig().lock(false, null)) {
				int i = 0;
				for (CollectionElement<ObservableTextStructure> el : getConfig().getContent().elements()) {
					if (thePathElement.matches(el.get())) {
						if (i == index)
							return new TSCollectionElement(el.get());
						i++;
					}
				}
				throw new IndexOutOfBoundsException(index + " of " + i);
			}
		}

		@Override
		public int getElementsBefore(ElementId id) {
			try (Transaction t = getConfig().lock(false, null)) {
				int idx = getConfig().getContent().getElementsBefore(id);
				int i = 0;
				int matches = 0;
				for (CollectionElement<ObservableTextStructure> el : getConfig().getContent().elements()) {
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
				ObservableTextStructure config = CollectionElement.get(getConfig().getContent().getElement(id));
				if (config == null || !thePathElement.matches(config))
					throw new NoSuchElementException();
				int i = 0;
				for (CollectionElement<ObservableTextStructure> el : getConfig().getContent().reverse().elements()) {
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
		public CollectionElement<ObservableTextStructure> getElement(ObservableTextStructure value, boolean first) {
			if (!thePathElement.matches(value))
				return null;
			try (Transaction t = getConfig().lock(false, null)) {
				ObservableTextStructure config = CollectionElement.get(getConfig().getContent().getElement(value, first));
				return config == null ? null : new TSCollectionElement(config);
			}
		}

		@Override
		public CollectionElement<ObservableTextStructure> getElement(ElementId id) {
			try (Transaction t = getConfig().lock(false, null)) {
				ObservableTextStructure config = CollectionElement.get(getConfig().getContent().getElement(id));
				if (!thePathElement.matches(config))
					throw new NoSuchElementException();
				return new TSCollectionElement(config);
			}
		}

		@Override
		public BetterList<CollectionElement<ObservableTextStructure>> getElementsBySource(ElementId sourceEl,
			BetterCollection<?> sourceCollection) {
			if (sourceCollection == this)
				return BetterList.of(getElement(sourceEl));
			return QommonsUtils.filterMap(getConfig().getContent().getElementsBySource(sourceEl, sourceCollection),
				el -> thePathElement.matches(el.get()), el -> new TSCollectionElement(el.get()));
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
			ObservableTextStructure config = getConfig().getContent().getElement(found).get();
			if (!thePathElement.matches(config))
				return null;
			return equivalentEl;
		}

		@Override
		public CollectionElement<ObservableTextStructure> getTerminalElement(boolean first) {
			try (Transaction t = getConfig().lock(false, null)) {
				ObservableTextStructure config = CollectionElement.get(getConfig().getContent().getTerminalElement(first));
				while (config != null && !thePathElement.matches(config))
					config = CollectionElement.get(getConfig().getContent().getAdjacentElement(config.getParentChildRef(), first));
				return config == null ? null : new TSCollectionElement(config);
			}
		}

		@Override
		public CollectionElement<ObservableTextStructure> getAdjacentElement(ElementId elementId, boolean next) {
			try (Transaction t = getConfig().lock(false, null)) {
				ObservableTextStructure config = CollectionElement.get(getConfig().getContent().getAdjacentElement(elementId, next));
				while (config != null && !thePathElement.matches(config))
					config = CollectionElement.get(getConfig().getContent().getAdjacentElement(config.getParentChildRef(), next));
				return config == null ? null : new TSCollectionElement(config);
			}
		}

		@Override
		public MutableCollectionElement<ObservableTextStructure> mutableElement(ElementId id) {
			try (Transaction t = getConfig().lock(false, null)) {
				ObservableTextStructure config = CollectionElement.get(getConfig().getContent().getElement(id));
				if (!thePathElement.matches(config))
					throw new NoSuchElementException();
				return new MutableTSCollectionElement(config, this);
			}
		}

		@Override
		public String canAdd(ObservableTextStructure value, ElementId after, ElementId before) {
			return StdMsg.UNSUPPORTED_OPERATION;
		}

		@Override
		public CollectionElement<ObservableTextStructure> addElement(ObservableTextStructure value, ElementId after, ElementId before,
			boolean first)
				throws UnsupportedOperationException, IllegalArgumentException {
			throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
		}

		@Override
		public String canMove(ElementId valueEl, ElementId after, ElementId before) {
			return null;
		}

		@Override
		public CollectionElement<ObservableTextStructure> move(ElementId valueEl, ElementId after, ElementId before, boolean first,
			Runnable afterRemove)
				throws UnsupportedOperationException, IllegalArgumentException {
			ObservableTextStructure valueConfig = getConfig().getContent().getElement(valueEl).get();
			ObservableTextStructure afterConfig = after == null ? null : getConfig().getContent().getElement(after).get();
			ObservableTextStructure beforeConfig = before == null ? null : getConfig().getContent().getElement(before).get();
			return new TSCollectionElement(getConfig()//
				.moveChild(valueConfig, afterConfig, beforeConfig, first, afterRemove));
		}

		@Override
		public void setValue(Collection<ElementId> elements, ObservableTextStructure value) {
			throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
		}

		@Override
		public void clear() {
			try (Transaction t = getConfig().lock(true, null)) {
				for (CollectionElement<ObservableTextStructure> el : getConfig().getContent().elements()) {
					if (thePathElement.matches(el.get()))
						el.get().remove();
				}
			}
		}

		@Override
		public Subscription onChange(Consumer<? super ObservableCollectionEvent<? extends ObservableTextStructure>> observer) {
			String watchPath = thePathElement.getName() + ObservableTextPath.PATH_SEPARATOR + ObservableTextPath.ANY_DEPTH;
			return getConfig().watch(ObservableTextPath.createPath(watchPath)).act(evt -> {
				ObservableTextStructure child = evt.relativePath.get(0);
				boolean postMatches = thePathElement.matches(child);
				boolean preMatches = evt.changeType == CollectionChangeType.set ? thePathElement.matchedBefore(child, evt)
					: postMatches;
				if (preMatches || postMatches) {
					int index;
					if (postMatches)
						index = getElementsBefore(child.getParentChildRef());
					else {
						int i = 0;
						for (CollectionElement<ObservableTextStructure> el : getConfig().getContent().elements()) {
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

					ObservableTextStructure oldValue = changeType == CollectionChangeType.add ? null : child;
					ObservableCollectionEvent<ObservableTextStructure> collEvt = new ObservableCollectionEvent<>(child.getParentChildRef(),
						getType(), index,
						changeType, preMatches && evt.isMove, oldValue, child, evt);
					try (Transaction t = collEvt.use()) {
						observer.accept(collEvt);
					}
				}
			});
		}
	}

	/** Implements the value set portion of {@link ObservableTextStructure#getContent(ObservableTextPath)} */
	protected static class ObservableChildSet implements SyncValueSet<ObservableTextStructure> {
		private final ObservableTextStructure theRoot;
		private final ObservableTextPath thePath;
		private final ObservableCollection<ObservableTextStructure> theChildren;

		/**
		 * @param root The root config
		 * @param path The path for the values
		 * @param children The child collection
		 */
		public ObservableChildSet(ObservableTextStructure root, ObservableTextPath path,
			ObservableCollection<ObservableTextStructure> children) {
			theRoot = root;
			thePath = path;
			theChildren = children;
		}

		/** @return The root config */
		protected ObservableTextStructure getRoot() {
			return theRoot;
		}

		/** @return the path for the values */
		protected ObservableTextPath getPath() {
			return thePath;
		}

		@Override
		public ConfiguredValueType<ObservableTextStructure> getType() {
			return new ConfiguredValueType<ObservableTextStructure>() {
				@Override
				public TypeToken<ObservableTextStructure> getType() {
					return TypeTokens.get().of(ObservableTextStructure.class);
				}

				@Override
				public List<? extends ConfiguredValueType<? super ObservableTextStructure>> getSupers() {
					return Collections.emptyList();
				}

				@Override
				public QuickMap<String, ? extends ConfiguredValueField<ObservableTextStructure, ?>> getFields() {
					return QuickSet.<String> empty().createMap();
				}

				@Override
				public <F> ConfiguredValueField<ObservableTextStructure, F> getField(
					Function<? super ObservableTextStructure, F> fieldGetter) {
					throw new UnsupportedOperationException("No typed fields for an " + ObservableTextStructure.class.getSimpleName());
				}

				@Override
				public boolean allowsCustomFields() {
					return true;
				}
			};
		}

		@Override
		public ObservableCollection<ObservableTextStructure> getValues() {
			return theChildren;
		}

		@Override
		public <C2 extends ObservableTextStructure> SyncValueCreator<ObservableTextStructure, C2> create(TypeToken<C2> subType) {
			if (TypeTokens.getRawType(subType) != ObservableTextStructure.class)
				throw new IllegalArgumentException(
					"Unrecognized " + ObservableTextStructure.class.getSimpleName() + " sub-type " + subType);
			return (SyncValueCreator<ObservableTextStructure, C2>) new SyncValueCreator<ObservableTextStructure, ObservableTextStructure>() {
				private Map<String, String> theFields;
				private ElementId theAfter;
				private ElementId theBefore;
				private boolean isTowardBeginning;

				@Override
				public ConfiguredValueType<ObservableTextStructure> getType() {
					return ObservableChildSet.this.getType();
				}

				@Override
				public Set<Integer> getRequiredFields() {
					return Collections.emptySet();
				}

				@Override
				public SyncValueCreator<ObservableTextStructure, ObservableTextStructure> after(ElementId after) {
					theAfter = after;
					return this;
				}

				@Override
				public SyncValueCreator<ObservableTextStructure, ObservableTextStructure> before(ElementId before) {
					theBefore = before;
					return this;

				}

				@Override
				public SyncValueCreator<ObservableTextStructure, ObservableTextStructure> towardBeginning(boolean towardBeginning) {
					isTowardBeginning = towardBeginning;
					return this;
				}

				@Override
				public String isEnabled(ConfiguredValueField<? super ObservableTextStructure, ?> field) {
					return null;
				}

				@Override
				public <F> String isAcceptable(ConfiguredValueField<? super ObservableTextStructure, F> field, F value) {
					return null;
				}

				@Override
				public SyncValueCreator<ObservableTextStructure, ObservableTextStructure> with(String fieldName, Object value)
					throws IllegalArgumentException {
					if (theFields == null)
						theFields = new LinkedHashMap<>();
					theFields.put(fieldName, String.valueOf(value));
					return this;
				}

				@Override
				public <F> SyncValueCreator<ObservableTextStructure, ObservableTextStructure> with(
					ConfiguredValueField<ObservableTextStructure, F> field, F value) throws IllegalArgumentException {
					throw new UnsupportedOperationException();
				}

				@Override
				public <F> SyncValueCreator<ObservableTextStructure, ObservableTextStructure> with(
					Function<? super ObservableTextStructure, F> field, F value) throws IllegalArgumentException {
					throw new UnsupportedOperationException();
				}

				@Override
				public String canCreate() {
					return null;
				}

				@Override
				public CollectionElement<ObservableTextStructure> create(Consumer<? super ObservableTextStructure> preAddAction) {
					ObservableTextStructure afterChild = theAfter == null ? null : theChildren.getElement(theAfter).get();
					ObservableTextStructure beforeChild = theBefore == null ? null : theChildren.getElement(theBefore).get();
					ElementId newChildId;
					try (Transaction t = theRoot.lock(true, null)) {
						ObservableTextStructure parent = thePath.getParent() == null ? theRoot
							: theRoot.getChild(thePath.getParent(), true, null);
						ObservableTextStructure newChild = parent.addChild(afterChild, beforeChild, isTowardBeginning,
							thePath.getLastElement().getName(),
							cfg -> {
								if (theFields != null)
									for (Map.Entry<String, String> field : theFields.entrySet())
										cfg.set(field.getKey(), field.getValue());
								if (preAddAction != null)
									preAddAction.accept(cfg);
							});
						newChildId = theChildren.getElementsBySource(newChild.getParentChildRef(), parent.getContent()).getFirst()
							.getElementId();
					}
					return theChildren.getElement(newChildId);
				}
			};
		}

		static void copy(ObservableTextStructure source, ObservableTextStructure dest) {
			dest.setValue(source.getValue());
			for (int i = 0; i < source.getContent().size(); i++) {
				ObservableTextStructure srcChild = source.getContent().get(i);
				ObservableTextStructure destChild = dest.addChild(srcChild.getName());
				copy(srcChild, destChild);
			}
		}
	}
}
