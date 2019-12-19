package org.observe.config;

import java.text.ParseException;
import java.util.Collection;
import java.util.Iterator;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.ObservableValueEvent;
import org.observe.Observer;
import org.observe.SettableValue;
import org.observe.SimpleObservable;
import org.observe.Subscription;
import org.observe.collect.CollectionChangeType;
import org.observe.collect.Equivalence;
import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableCollectionEvent;
import org.observe.config.ObservableConfig.ObservableConfigEvent;
import org.observe.config.ObservableConfigFormat.EntityConfigFormat;
import org.observe.util.ObservableCollectionWrapper;
import org.observe.util.TypeTokens;
import org.qommons.Causable;
import org.qommons.Identifiable;
import org.qommons.QommonsUtils;
import org.qommons.Stamped;
import org.qommons.Transactable;
import org.qommons.Transaction;
import org.qommons.ValueHolder;
import org.qommons.collect.BetterCollection;
import org.qommons.collect.BetterList;
import org.qommons.collect.BetterSortedList;
import org.qommons.collect.BetterSortedMap;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.ElementId;
import org.qommons.collect.ListenerList;
import org.qommons.collect.MutableCollectionElement;
import org.qommons.collect.MutableCollectionElement.StdMsg;
import org.qommons.tree.BetterTreeMap;

import com.google.common.reflect.TypeToken;

public abstract class ObservableConfigTransform implements Transactable, Stamped {
	private final ObservableConfig theRoot;
	private final ObservableValue<? extends ObservableConfig> theParent;
	private final Runnable theParentCreate;
	private final Observable<?> theUntil;

	private long theStamp;

	private boolean _isConnected;
	private ObservableValue<Boolean> isConnected;

	public ObservableConfigTransform(ObservableConfig root, ObservableValue<? extends ObservableConfig> parent, Runnable ceCreate,
		Observable<?> until) {
		theRoot = root;
		theParent = parent;
		theParentCreate = ceCreate;
		if (until == null) {
			isConnected = ObservableValue.of(true);
			theUntil = theParent.noInitChanges().filter(evt -> evt.getOldValue() != evt.getNewValue());
		} else {
			isConnected = ObservableValue.of(TypeTokens.get().BOOLEAN, () -> _isConnected, () -> _isConnected ? 0 : 1, until.take(1));
			theUntil = Observable.or(until, //
				theParent.noInitChanges().takeUntil(until).filter(evt -> evt.getOldValue() != evt.getNewValue()));
		}
	}

	protected void init(Observable<?> until, boolean listen, Observable<?> findRefs) {
		_isConnected = true;
		until.take(1).act(__ -> _isConnected = false);
		boolean[] initialized = new boolean[1];
		findRefs.act(__ -> initialized[0] = true);
		theParent.changes().takeUntil(until).act(//
			evt -> {
				if (evt.isInitial()) {} else if (evt.getNewValue() == evt.getOldValue()) {
					return;
				} else
					theStamp++;
				ObservableConfig newParent = evt.getNewValue();
				try (Transaction ceT = newParent == null ? Transaction.NONE : newParent.lock(false, null)) {
					initConfig(evt.getNewValue(), evt, initialized[0] ? Observable.constant(null) : findRefs);
					if (listen && newParent != null)
						newParent.watch("").takeUntil(theUntil).act(this::onChange);
				}
			});
	}

	public ObservableValue<Boolean> isConnected() {
		return isConnected;
	}

	protected ObservableConfig getRoot() {
		return theRoot;
	}

	protected void incrementStamp() {
		theStamp++;
	}

	protected ObservableValue<? extends ObservableConfig> getParent() {
		return theParent;
	}

	protected ObservableConfig getParent(boolean createIfAbsent, Consumer<ObservableConfig> parentAction) {
		if (!createIfAbsent && parentAction == null) {
			return theParent.get();
		}
		Transaction parentLock = theRoot.lock(createIfAbsent, null);
		try {
			ObservableConfig parent = theParent.get();
			while (parent == null && createIfAbsent) {
				parentLock.close();
				parentLock = null;
				theParentCreate.run();
				parent = theParent.get();
			}
			if (parent != null && parentAction != null)
				parentAction.accept(parent);
			return parent;
		} finally {
			if (parentLock != null)
				parentLock.close();
		}
	}

	protected Observable<?> getUntil() {
		return theUntil;
	}

	@Override
	public boolean isLockSupported() {
		return true;
	}

	@Override
	public Transaction lock(boolean write, Object cause) {
		return theRoot.lock(write, cause);
	}

	@Override
	public Transaction tryLock(boolean write, Object cause) {
		return theRoot.tryLock(write, cause);
	}

	@Override
	public long getStamp() {
		return theStamp;
	}

	protected abstract void initConfig(ObservableConfig parent, Object cause, Observable<?> findRefs);

	protected abstract void onChange(ObservableConfigEvent parentChange);

	static class ObservableConfigValue<E> extends ObservableConfigTransform implements SettableValue<E> {
		private final TypeToken<E> theType;
		private final ObservableConfigFormat<E> theFormat;

		private final ListenerList<Observer<? super ObservableValueEvent<E>>> theListeners;

		private E theValue;
		private final ValueHolder<E> theModifyingValue;

		private Object theIdentity;
		private Object theChangesIdentity;

		public ObservableConfigValue(ObservableConfig root, ObservableValue<? extends ObservableConfig> parent, Runnable ceCreate,
			Observable<?> until, TypeToken<E> type, ObservableConfigFormat<E> format, boolean listen, Observable<?> findRefs) {
			super(root, parent, ceCreate, until);
			theType = type;
			theFormat = format;

			theListeners = ListenerList.build().allowReentrant().withFastSize(false).build();
			theModifyingValue = new ValueHolder<>();

			init(until == null ? Observable.empty() : until, listen, findRefs);
		}

		@Override
		public Object getIdentity() {
			if (theIdentity == null)
				theIdentity = Identifiable.wrap(getParent().getIdentity(), "value", theFormat);
			return theIdentity;
		}

		@Override
		public TypeToken<E> getType() {
			return theType;
		}

		@Override
		public E get() {
			return theValue;
		}

		@Override
		public Observable<ObservableValueEvent<E>> noInitChanges() {
			class OCVChanges extends AbstractIdentifiable implements Observable<ObservableValueEvent<E>> {
				@Override
				protected Object createIdentity() {
					if (theChangesIdentity == null)
						theChangesIdentity = Identifiable.wrap(ObservableConfigValue.this.getIdentity(), "noInitChanges");
					return theChangesIdentity;
				}

				@Override
				public boolean isSafe() {
					return true;
				}

				@Override
				public boolean isLockSupported() {
					return ObservableConfigValue.this.isLockSupported();
				}

				@Override
				public Transaction lock() {
					return ObservableConfigValue.this.lock(false, null);
				}

				@Override
				public Transaction tryLock() {
					return ObservableConfigValue.this.tryLock(false, null);
				}

				@Override
				public Subscription subscribe(Observer<? super ObservableValueEvent<E>> observer) {
					return theListeners.add(observer, true)::run;
				}
			}
			return new OCVChanges();
		}

		@Override
		public <V extends E> E set(V value, Object cause) throws IllegalArgumentException, UnsupportedOperationException {
			if (!isConnected().get())
				throw new UnsupportedOperationException("Not connected");
			Object[] oldValue = new Object[1];
			try (Transaction t = lock(true, cause)) {
				getParent(true, parent -> {
					try (Transaction parentT = parent.lock(true, cause)) {
						E oldV = theValue;
						oldValue[0] = oldV;
						theFormat.format(value, oldV, parent, theModifyingValue, getUntil());
					} finally {
						theModifyingValue.clear();
					}
				});
			}
			return (E) oldValue[0];
		}

		@Override
		public <V extends E> String isAcceptable(V value) {
			if (!isConnected().get())
				return "Not connected";
			if (!TypeTokens.get().isInstance(theType, value))
				return StdMsg.ILLEGAL_ELEMENT;
			return null;
		}

		@Override
		public ObservableValue<String> isEnabled() {
			return isConnected().map(c -> c ? null : "Not connected");
		}

		@Override
		protected void initConfig(ObservableConfig parent, Object cause, Observable<?> findRefs) {
			try {
				theValue = theFormat
					.parse(ObservableConfigFormat.ctxFor(parent, getParent(), () -> getParent(true, null), null, getUntil(), theValue,
						findRefs, v -> theValue = v));
			} catch (ParseException e) {
				e.printStackTrace();
				theValue = null;
			}
		}

		@Override
		protected void onChange(ObservableConfigEvent parentChange) {
			E oldValue = theValue;
			if (!theModifyingValue.isPresent()) {
				try {
					theValue = theFormat.parse(ObservableConfigFormat.ctxFor(getRoot(), getParent(), () -> getParent(true, null),
						parentChange, getUntil(), theValue, Observable.constant(null), null));
					fire(createChangeEvent(oldValue, theValue, parentChange));
				} catch (ParseException e) {
					e.printStackTrace();
				}
			} else {
				theValue = theModifyingValue.get();
				fire(createChangeEvent(oldValue, theValue, parentChange));
			}
		}

		private void fire(ObservableValueEvent<E> event) {
			try (Transaction t = Causable.use(event)) {
				theListeners.forEach(//
					listener -> listener.onNext(event));
			}
		}

		@Override
		public int hashCode() {
			return getIdentity().hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			return obj instanceof Identifiable && getIdentity().equals(((Identifiable) obj).getIdentity());
		}

		@Override
		public String toString() {
			return getIdentity().toString();
		}
	}

	static abstract class ObservableConfigBackedCollection<E> extends ObservableConfigTransform {
		final TypeToken<E> theType;
		private final ObservableConfigFormat<E> theFormat;
		private final String theChildName;

		private final BetterSortedMap<ElementId, ConfigElement> theElements;
		private final ListenerList<Consumer<? super ObservableCollectionEvent<? extends E>>> theListeners;
		private final OCBCCollection theCollection;

		ConfigElement theNewElement;
		Consumer<? super ConfigElement> thePreAddAction;

		public ObservableConfigBackedCollection(ObservableConfig root, ObservableValue<? extends ObservableConfig> collectionElement,
			Runnable ceCreate, TypeToken<E> type, ObservableConfigFormat<E> format, String childName, Observable<?> until, boolean listen,
			Observable<?> findRefs) {
			super(root, collectionElement, ceCreate, until);
			theType = type;
			theFormat = format;
			theChildName = childName;

			theElements = new BetterTreeMap<>(false, ElementId::compareTo);
			theListeners = ListenerList.build().allowReentrant().withFastSize(false).build();

			init(until, listen, findRefs);

			theCollection = createCollection();
		}

		@Override
		protected void initConfig(ObservableConfig collectionElement, Object cause, Observable<?> findRefs) {
			if (!theElements.isEmpty()) {
				Iterator<ConfigElement> cveIter = theElements.values().reverse().iterator();
				while (cveIter.hasNext()) {
					ConfigElement cve = cveIter.next();
					cveIter.remove();
					cve.dispose();
					fire(new ObservableCollectionEvent<>(cve.getElementId(), theType, theElements.size(), CollectionChangeType.remove,
						cve.get(), cve.get(), cause));
				}
			}
			if (collectionElement != null) {
				for (ObservableConfig child : collectionElement.getContent(theChildName).getValues()) {
					ConfigElement cve = createElement(child, null, findRefs);
					cve.theElement = theElements.putEntry(child.getParentChildRef(), cve, false).getElementId();
					fire(new ObservableCollectionEvent<>(cve.getElementId(), theType, theElements.size() - 1, CollectionChangeType.add,
						null, cve.get(), cause));
				}
			}
		}

		@Override
		protected void onChange(ObservableConfigEvent collectionChange) {
			if (collectionChange.relativePath.isEmpty() || collectionChange.eventTarget != getParent(false, null))
				return; // Doesn't affect us
			boolean elementChange = collectionChange.relativePath.size() == 1;
			ObservableConfig config = collectionChange.relativePath.get(0);
			if (elementChange && collectionChange.changeType == CollectionChangeType.add) {
				ConfigElement newEl;
				if (theNewElement != null)
					newEl = theNewElement;
				else
					newEl = createElement(config, null, Observable.constant(null));
				initialize(newEl, thePreAddAction, collectionChange);
			} else {
				CollectionElement<ConfigElement> el = theElements.getEntry(config.getParentChildRef());
				if (el == null) // Must be a different child
					return;
				if (collectionChange.relativePath.size() == 1 && collectionChange.changeType == CollectionChangeType.remove) {
					incrementStamp();
					theElements.mutableEntry(el.getElementId()).remove();
					el.get().dispose();
					fire(new ObservableCollectionEvent<>(el.getElementId(), theType,
						theElements.keySet().getElementsBefore(el.getElementId()), CollectionChangeType.remove, el.get().get(),
						el.get().get(), collectionChange));
				} else {
					try {
						E newValue;
						if (el.get().modifying != null) {
							newValue = el.get().modifying.get();
							el.get().modifying = null;
						} else {
							ObservableConfigEvent childChange = collectionChange.asFromChild();
							try (Transaction ct = Causable.use(childChange)) {
								newValue = theFormat.parse(ObservableConfigFormat.ctxFor(getRoot(), //
									ObservableValue.of(el.get().getConfig()), () -> collectionChange.eventTarget.addChild(theChildName),
									childChange, getUntil(), el.get().get(), Observable.constant(null), null));
							}
						}
						E oldValue = el.get().get();
						incrementStamp();
						if (newValue != oldValue)
							el.get()._set(newValue);
						fire(new ObservableCollectionEvent<>(el.getElementId(), theType,
							theElements.keySet().getElementsBefore(el.getElementId()), CollectionChangeType.set, oldValue, newValue,
							collectionChange));
					} catch (ParseException e) {
						e.printStackTrace();
					}
				}
			}
		}

		private void initialize(ConfigElement newEl, Consumer<? super ConfigElement> preAddAction, Object cause) {
			if (thePreAddAction != null) {
				thePreAddAction.accept(newEl);
				thePreAddAction = null;
			}
			ElementId newElId;
			ObservableConfig config = newEl.getConfig();
			CollectionElement<ElementId> el = theElements.keySet().search(config.getParentChildRef(),
				BetterSortedList.SortedSearchFilter.PreferLess);
			if (el == null)// Must be empty
				newElId = theElements.putEntry(config.getParentChildRef(), newEl, false).getElementId();
			else if (el.get().compareTo(config.getParentChildRef()) < 0)
				newElId = theElements.putEntry(config.getParentChildRef(), newEl, el.getElementId(), null, true).getElementId();
			else
				newElId = theElements.putEntry(config.getParentChildRef(), newEl, null, el.getElementId(), false).getElementId();
			newEl.theElement = newElId;
			incrementStamp();
			fire(new ObservableCollectionEvent<>(newElId, theType, theElements.keySet().getElementsBefore(newElId),
				CollectionChangeType.add, null, newEl.get(), cause));
		}

		private void fire(ObservableCollectionEvent<E> event) {
			try (Transaction t = Causable.use(event)) {
				theListeners.forEach(//
					listener -> listener.accept(event));
			}
		}

		protected ObservableConfigFormat<E> getFormat() {
			return theFormat;
		}

		public Subscription addListener(Consumer<? super ObservableCollectionEvent<? extends E>> listener) {
			return theListeners.add(listener, true)::run;
		}

		public ObservableCollection<E> getCollection() {
			return theCollection;
		}

		protected ConfigElement add(Function<ObservableConfig, E> value, ElementId after, ElementId before, boolean first,
			Consumer<ConfigElement> preAddAction) {
			if (!isConnected().get())
				throw new UnsupportedOperationException("Not connected");
			ConfigElement[] cve = new ObservableConfigBackedCollection.ConfigElement[1];
			getParent(true, parent -> {
				try (Transaction t = parent.lock(true, null)) {
					if (after != null && !after.isPresent())
						throw new IllegalStateException("Collection has changed: " + after + " is no longer present");
					if (before != null && !before.isPresent())
						throw new IllegalStateException("Collection has changed: " + before + " is no longer present");
					ObservableConfig configAfter = after == null ? null : theElements.getEntryById(after).get().getConfig();
					ObservableConfig configBefore = before == null ? null : theElements.getEntryById(before).get().getConfig();
					thePreAddAction = preAddAction;
					parent.addChild(configAfter, configBefore, first, theChildName, cfg -> {
						theNewElement = createElement(cfg, new ValueHolder<>(value.apply(cfg)), Observable.constant(null));
					});
					cve[0] = theNewElement;
					theNewElement = null;
					if (!cve[0].isInitialized()) {
						// This can happen when using pre-add actions,
						// i.e. this add invocation itself is happening inside a pre-add action from an add operation
						// higher up in the config hierarchy.
						// Value sets that are a field in an entity rely on the parent to pass change events to it
						// and in such a case, the parent config isn't yet accounted for in the config value hierarchy,
						// so the event can't be handled by the onChange method
						initialize(cve[0], preAddAction, null);
					}
				}
			});
			return cve[0];
		}

		protected abstract OCBCCollection createCollection();

		protected abstract ConfigElement createElement(ObservableConfig config, ValueHolder<E> value, Observable<?> findRefs);

		@Override
		public int hashCode() {
			return getCollection().hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			else if (!(obj instanceof ObservableConfigBackedCollection))
				return false;
			return getCollection().equals(((ObservableConfigBackedCollection<?>) obj).getCollection());
		}

		@Override
		public String toString() {
			return getCollection().toString();
		}

		protected abstract class ConfigElement implements MutableCollectionElement<E> {
			private final ObservableConfig theConfig;
			private final SimpleObservable<Void> theElementObservable;
			private ElementId theElement;
			private E theValue;
			private CollectionElement<E> immutable;
			ValueHolder<E> modifying;

			public ConfigElement(ObservableConfig config, ValueHolder<E> value, Observable<?> findRefs) {
				this.theConfig = config;
				theElementObservable = new SimpleObservable<>(null, null, false, null, ListenerList.build().unsafe());

				if (value != null && value.isPresent()) {
					theFormat.format(value.get(), null, config, v -> theValue = v, Observable.or(getUntil(), theElementObservable));
				} else {
					E val;
					try {
						val = theFormat.parse(ObservableConfigFormat.ctxFor(getRoot(), //
							ObservableValue.of(this.theConfig), () -> this.theConfig.getParent().addChild(theChildName), null,
							Observable.or(getUntil(), theElementObservable), null, findRefs, this::_set));
					} catch (ParseException e) {
						System.err.println("Could not parse instance for " + this.theConfig);
						e.printStackTrace();
						val = null;
					}
					theValue = val;
				}
			}

			boolean isInitialized() {
				return theElement != null;
			}

			protected ObservableConfig getConfig() {
				return theConfig;
			}

			@Override
			public BetterCollection<E> getCollection() {
				return ObservableConfigBackedCollection.this.getCollection();
			}

			@Override
			public ElementId getElementId() {
				return theElement;
			}

			@Override
			public E get() {
				return theValue;
			}

			protected void _set(E value) {
				theValue = value;
			}

			protected void setOp(E value) throws UnsupportedOperationException, IllegalArgumentException {
				if (!isConnected().get())
					throw new UnsupportedOperationException("Not connected");
				try (Transaction t = lock(true, null)) {
					if (!theConfig.getParentChildRef().isPresent())
						throw new IllegalArgumentException(StdMsg.ELEMENT_REMOVED);
					modifying = new ValueHolder<>(value);
					theFormat.format(//
						value, get(), theConfig, v -> {}, Observable.or(getUntil(), theElementObservable));
				}
			}

			protected void removeOp() throws UnsupportedOperationException {
				if (!isConnected().get())
					throw new UnsupportedOperationException("Not connected");
				theConfig.remove();
			}

			void dispose() {
				theElementObservable.onNext(null);
			}

			@Override
			public CollectionElement<E> immutable() {
				if (immutable == null) {
					immutable = new CollectionElement<E>() {
						@Override
						public ElementId getElementId() {
							return theElement;
						}

						@Override
						public E get() {
							return theValue;
						}
					};
				}
				return immutable;
			}

			@Override
			public int hashCode() {
				return theElement.hashCode();
			}

			@Override
			public boolean equals(Object obj) {
				return obj instanceof CollectionElement && theElement.equals(((CollectionElement<?>) obj).getElementId());
			}

			@Override
			public String toString() {
				return new StringBuilder().append('[').append(theElements.keySet().getElementsBefore(theElement)).append("]=")
					.append(theValue).toString();
			}
		}

		protected abstract class OCBCCollection implements ObservableCollection<E> {
			private Object theIdentity;

			@Override
			public Object getIdentity() {
				if (theIdentity == null)
					theIdentity = Identifiable.wrap(getParent().getIdentity(), "values", theFormat, theChildName);
				return theIdentity;
			}

			@Override
			public long getStamp() {
				return ObservableConfigBackedCollection.this.getStamp();
			}

			@Override
			public boolean isLockSupported() {
				return true;
			}

			@Override
			public Transaction lock(boolean write, Object cause) {
				return ObservableConfigBackedCollection.this.lock(write, cause);
			}

			@Override
			public Transaction tryLock(boolean write, Object cause) {
				return ObservableConfigBackedCollection.this.tryLock(write, cause);
			}

			@Override
			public TypeToken<E> getType() {
				return theType;
			}

			@Override
			public boolean isContentControlled() {
				return false;
			}

			@Override
			public int size() {
				return theElements.size();
			}

			@Override
			public boolean isEmpty() {
				return theElements.isEmpty();
			}

			@Override
			public int getElementsBefore(ElementId id) {
				try (Transaction t = lock(false, null)) {
					return theElements.keySet().getElementsBefore(id);
				}
			}

			@Override
			public int getElementsAfter(ElementId id) {
				try (Transaction t = lock(false, null)) {
					return theElements.keySet().getElementsAfter(id);
				}
			}

			@Override
			public CollectionElement<E> getElement(int index) {
				try (Transaction t = lock(false, null)) {
					return theElements.getEntryById(theElements.keySet().getElement(index).getElementId()).get();
				}
			}

			@Override
			public CollectionElement<E> getElement(E value, boolean first) {
				try (Transaction t = lock(false, null)) {
					CollectionElement<E> el = getTerminalElement(first);
					while (el != null && !Objects.equals(el.get(), value))
						el = getAdjacentElement(el.getElementId(), first);
					return el;
				}
			}

			@Override
			public CollectionElement<E> getElement(ElementId id) {
				return theElements.getEntryById(id).get().immutable();
			}

			@Override
			public CollectionElement<E> getTerminalElement(boolean first) {
				CollectionElement<ConfigElement> el = theElements.values().getTerminalElement(first);
				return el == null ? null : el.get().immutable();
			}

			@Override
			public CollectionElement<E> getAdjacentElement(ElementId elementId, boolean next) {
				CollectionElement<ConfigElement> el = theElements.values().getAdjacentElement(elementId, next);
				return el == null ? null : el.get().immutable();
			}

			@Override
			public MutableCollectionElement<E> mutableElement(ElementId id) {
				return theElements.getEntryById(id).get();
			}

			@Override
			public BetterList<CollectionElement<E>> getElementsBySource(ElementId sourceEl) {
				BetterList<CollectionElement<ConfigElement>> els = theElements.values().getElementsBySource(sourceEl);
				if (!els.isEmpty())
					return QommonsUtils.map2(els, el -> el.get().immutable());
				ConfigElement el = theElements.get(sourceEl);
				return el == null ? BetterList.empty() : BetterList.of(el.immutable());
			}

			@Override
			public BetterList<ElementId> getSourceElements(ElementId localElement, BetterCollection<?> sourceCollection) {
				BetterCollection<ConfigElement> values = theElements.values();
				if (sourceCollection == this)
					return values.getSourceElements(localElement, values);
				return values.getSourceElements(localElement, sourceCollection);
			}

			@Override
			public Subscription onChange(Consumer<? super ObservableCollectionEvent<? extends E>> observer) {
				return addListener(observer);
			}

			@Override
			public void clear() {
				try (Transaction t = lock(true, null)) {
					for (CollectionElement<ConfigElement> el : theElements.values().reverse().elements()) {
						el.get().remove();
					}
				}
			}

			@Override
			public Equivalence<? super E> equivalence() {
				return Equivalence.DEFAULT;
			}

			@Override
			public void setValue(Collection<ElementId> elements, E value) {
				try (Transaction t = lock(true, null)) {
					for (ElementId el : elements) {
						theElements.getEntryById(el).get().set(value);
					}
				}
			}

			@Override
			public int hashCode() {
				return BetterCollection.hashCode(this);
			}

			@Override
			public boolean equals(Object obj) {
				return BetterCollection.equals(this, obj);
			}

			@Override
			public String toString() {
				return BetterCollection.toString(this);
			}
		}
	}

	static class ObservableConfigValues<E> extends ObservableCollectionWrapper<E> {
		private final Backing<E> theBacking;

		ObservableConfigValues(ObservableConfig root, ObservableValue<? extends ObservableConfig> collectionElement, Runnable ceCreate,
			TypeToken<E> type, ObservableConfigFormat<E> format, String childName, ObservableConfigFormatSet fieldParser,
			Observable<?> until, boolean listen, Observable<?> findRefs) {
			theBacking = new Backing<>(root, collectionElement, ceCreate, type, format, childName, until, listen, findRefs);

			init(theBacking.getCollection());
		}

		protected void onChange(ObservableConfigEvent collectionChange) {
			theBacking.onChange(collectionChange);
		}

		static class Backing<E> extends ObservableConfigBackedCollection<E> {
			Backing(ObservableConfig root, ObservableValue<? extends ObservableConfig> collectionElement, Runnable ceCreate,
				TypeToken<E> type, ObservableConfigFormat<E> format, String childName, Observable<?> until, boolean listen,
				Observable<?> findRefs) {
				super(root, collectionElement, ceCreate, type, format, childName, until, listen, findRefs);
			}

			@Override
			protected OCBCCollection createCollection() {
				return new OCBCCollection() {
					@Override
					public String canAdd(E value, ElementId after, ElementId before) {
						return belongs(value) ? null : StdMsg.ILLEGAL_ELEMENT;
					}

					@Override
					public CollectionElement<E> addElement(E value, ElementId after, ElementId before, boolean first)
						throws UnsupportedOperationException, IllegalArgumentException {
						if (!belongs(value))
							throw new IllegalArgumentException(StdMsg.ILLEGAL_ELEMENT);
						return Backing.this.add(cfg -> value, after, before, first, null);
					}
				};
			}

			@Override
			protected ConfigElement createElement(ObservableConfig config, ValueHolder<E> value, Observable<?> findRefs) {
				return new ConfigElement2(config, value, findRefs);
			}

			protected class ConfigElement2 extends ConfigElement {
				ConfigElement2(ObservableConfig config, ValueHolder<E> value, Observable<?> findRefs) {
					super(config, value, findRefs);
				}

				@Override
				public String isEnabled() {
					return isConnected().get() ? null : "Not connected";
				}

				@Override
				public String isAcceptable(E value) {
					if (!isConnected().get())
						return "Not connected";
					return getCollection().canAdd(value);
				}

				@Override
				public void set(E value) throws UnsupportedOperationException, IllegalArgumentException {
					if (!isConnected().get())
						throw new UnsupportedOperationException("Not connected");
					String msg = isAcceptable(value);
					if (msg != null)
						throw new IllegalArgumentException(msg);
					setOp(value);
				}

				@Override
				public String canRemove() {
					return null;
				}

				@Override
				public void remove() throws UnsupportedOperationException {
					removeOp();
				}
			}
		}
	}

	static class ObservableConfigEntityValues<E> extends ObservableConfigBackedCollection<E> implements ObservableValueSet<E> {
		ObservableConfigEntityValues(ObservableConfig root, ObservableValue<? extends ObservableConfig> collectionElement,
			Runnable ceCreate, EntityConfigFormat<E> format, String childName, Observable<?> until, boolean listen,
			Observable<?> findRefs) {
			super(root, collectionElement, ceCreate, format.getEntityType().getType(), format, childName, until, listen, findRefs);
		}

		@Override
		protected EntityConfigFormat<E> getFormat() {
			return (EntityConfigFormat<E>) super.getFormat();
		}

		@Override
		public EntityConfiguredValueType<E> getType() {
			return getFormat().getEntityType();
		}

		@Override
		public ObservableCollection<? extends E> getValues() {
			return getCollection();
		}

		@Override
		public <E2 extends E> ValueCreator<E, E2> create(TypeToken<E2> subType) {
			if (!isConnected().get())
				throw new UnsupportedOperationException("Not connected");
			return new SimpleValueCreator<E, E2>(getFormat().create(subType)) {
				@Override
				public CollectionElement<E> create(Consumer<? super E2> preAddAction) {
					return add(cfg -> {
						return createValue(cfg, getUntil());
					}, getAfter(), getBefore(), isTowardBeginning(), element -> {
						if (preAddAction != null)
							preAddAction.accept((E2) element.get());
					});
				}
			};
		}

		@Override
		public <E2 extends E> CollectionElement<E> copy(E2 template) {
			return create((TypeToken<E2>) TypeTokens.get().of(template.getClass())).create(v -> {
				getFormat().copy(template, v, getParent(), null, getUntil());
			});
		}

		@Override
		protected OCBCCollection createCollection() {
			return new ConfigValueCollection();
		}

		@Override
		protected ConfigElement createElement(ObservableConfig config, ValueHolder<E> value, Observable<?> findRefs) {
			return new ConfigValueElement(config, value, findRefs);
		}

		private class ConfigValueCollection extends OCBCCollection {
			@Override
			public String canAdd(E value, ElementId after, ElementId before) {
				return StdMsg.UNSUPPORTED_OPERATION;
			}

			@Override
			public CollectionElement<E> addElement(E value, ElementId after, ElementId before, boolean first)
				throws UnsupportedOperationException, IllegalArgumentException {
				throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
			}
		}

		private class ConfigValueElement extends ConfigElement {
			ConfigValueElement(ObservableConfig config, ValueHolder<E> value, Observable<?> findRefs) {
				super(config, value, findRefs);
			}

			@Override
			public String isEnabled() {
				return StdMsg.UNSUPPORTED_OPERATION;
			}

			@Override
			public String isAcceptable(E value) {
				return StdMsg.UNSUPPORTED_OPERATION;
			}

			@Override
			public void set(E value) throws UnsupportedOperationException, IllegalArgumentException {
				if (value == get()) {
					ObservableConfig parent = getConfig().getParent();
					if (parent != null)
						((ObservableCollection<ObservableConfig>) parent.getAllContent().getValues())
						.mutableElement(getConfig().getParentChildRef()).set(getConfig());
				} else
					throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
			}

			@Override
			public String canRemove() {
				return null;
			}

			@Override
			public void remove() throws UnsupportedOperationException {
				removeOp();
			}
		}
	}
}
