package org.observe.config;

import java.text.ParseException;
import java.util.Collection;
import java.util.Iterator;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

import org.observe.Equivalence;
import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.ObservableValueEvent;
import org.observe.Observer;
import org.observe.SettableValue;
import org.observe.SimpleObservable;
import org.observe.Subscription;
import org.observe.assoc.ObservableMap;
import org.observe.assoc.ObservableMapEvent;
import org.observe.assoc.ObservableMultiMap;
import org.observe.assoc.ObservableMultiMapEvent;
import org.observe.collect.CollectionChangeType;
import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableCollectionEvent;
import org.observe.collect.ObservableSet;
import org.observe.config.EntityConfiguredValueType.EntityConfiguredValueField;
import org.observe.config.ObservableConfig.ObservableConfigEvent;
import org.observe.config.ObservableConfigFormat.EntityConfigFormat;
import org.observe.config.ObservableConfigFormat.Impl;
import org.observe.config.ObservableConfigFormat.MapEntry;
import org.observe.util.ObservableCollectionWrapper;
import org.observe.util.TypeTokens;
import org.qommons.Identifiable;
import org.qommons.LambdaUtils;
import org.qommons.Lockable.CoreId;
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
import org.qommons.collect.MapEntryHandle;
import org.qommons.collect.MultiEntryHandle;
import org.qommons.collect.MultiEntryValueHandle;
import org.qommons.collect.MutableCollectionElement;
import org.qommons.collect.MutableCollectionElement.StdMsg;
import org.qommons.collect.MutableMapEntryHandle;
import org.qommons.tree.BetterTreeMap;

import com.google.common.reflect.TypeToken;

/** A super class for observable structures backed by an {@link ObservableConfig} */
public abstract class ObservableConfigTransform implements Transactable, Stamped {
	private final Transactable theLock;
	private final ObservableConfigParseSession theSession;
	private final ObservableValue<? extends ObservableConfig> theParent;
	private final Consumer<Boolean> theParentCreate;
	private final Observable<?> theUntil;

	private long theStamp;

	private boolean _isConnected;
	private ObservableValue<Boolean> isConnected;

	/**
	 * @param lock The lock to power this structure's transactionality
	 * @param session The session with which to associate config-backed values
	 * @param parent The parent config backing the structure
	 * @param ceCreate Creates the parent config if it does not exist
	 * @param until The until observable to release resources and listeners for config-backed structures
	 */
	public ObservableConfigTransform(Transactable lock, ObservableConfigParseSession session,
		ObservableValue<? extends ObservableConfig> parent, Consumer<Boolean> ceCreate, Observable<?> until) {
		theLock = lock;
		theSession = session;
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

	/**
	 * Begins the dynamic attachment of the config-backed structure to the config itself
	 *
	 * @param until The until value to stop listening for parent changes
	 * @param listen Whether to listen to the parent for changes
	 * @param findRefs The reference finder observable of the parsing context
	 */
	protected void init(Observable<?> until, boolean listen, Observable<?> findRefs) {
		_isConnected = true;
		until.take(1).act(__ -> _isConnected = false);
		boolean[] initialized = new boolean[1];
		findRefs.act(__ -> initialized[0] = true);
		theParent.changes().takeUntil(until).act(//
			evt -> {
				if (evt.isInitial()) {//
				} else if (evt.getNewValue() == evt.getOldValue()) {
					return;
				} else
					theStamp++;
				ObservableConfig newParent = evt.getNewValue();
				if (newParent != null && publishSelf())
					newParent.withParsedItem(theSession, this);
				try (Transaction ceT = newParent == null ? Transaction.NONE : newParent.lock(false, null)) {
					initConfig(evt.getNewValue(), evt, initialized[0] ? Observable.constant(null) : findRefs);
					if (listen && newParent != null)
						newParent.watch("").takeUntil(theUntil).act(LambdaUtils.printableConsumer(this::onChange, //
							() -> ObservableConfigTransform.this.getClass().getSimpleName() + "(" + newParent + ").onChange()", null));
				}
			});
	}

	/** @return Whether this structure should associate itself with its session in its config */
	protected boolean publishSelf() {
		return true;
	}

	/** @return Whether this structure is connected to its config, watching for changes */
	public ObservableValue<Boolean> isConnected() {
		return isConnected;
	}

	/** @return The lock powering this structure's transactionality */
	protected Transactable getLock() {
		return theLock;
	}

	/** @return The session with which this structure and its elements are associated in the config structure */
	public ObservableConfigParseSession getSession() {
		return theSession;
	}

	/** Increments this structure's stamp, indicating that it has changed */
	protected void incrementStamp() {
		theStamp++;
	}

	/** @return The parent config backing the structure */
	protected ObservableValue<? extends ObservableConfig> getParent() {
		return theParent;
	}

	/**
	 * @param createIfAbsent Whether to create the parent config if it does not exist
	 * @param trivial Whether the parent should be trivial if it must be created
	 * @param parentAction The action to perform on the parent (if it exists or createIfAbsent is true)
	 * @return The current (or new) parent config backing the structure
	 */
	protected ObservableConfig getParent(boolean createIfAbsent, boolean trivial, Consumer<ObservableConfig> parentAction) {
		if (!createIfAbsent && parentAction == null) {
			return theParent.get();
		}
		Transaction parentLock = lock(createIfAbsent, null);
		try {
			ObservableConfig parent = theParent.get();
			while (parent == null && createIfAbsent) {
				if (parentLock != null)
					parentLock.close();
				parentLock = null;
				theParentCreate.accept(trivial);
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

	/** @return The until observable to release all resources and listeners */
	protected Observable<?> getUntil() {
		return theUntil;
	}

	@Override
	public boolean isLockSupported() {
		return true;
	}

	@Override
	public Transaction lock(boolean write, Object cause) {
		return theLock.lock(write, cause);
	}

	@Override
	public Transaction tryLock(boolean write, Object cause) {
		return theLock.tryLock(write, cause);
	}

	@Override
	public CoreId getCoreId() {
		return theLock.getCoreId();
	}

	@Override
	public long getStamp() {
		return theStamp;
	}

	/**
	 * Initializes this structure from configuration
	 *
	 * @param parent The parent config of the structure
	 * @param cause The cause of the change (an initial or change event on the {@link #getParent() parent} value)
	 * @param findRefs The find refs observable
	 */
	protected abstract void initConfig(ObservableConfig parent, Object cause, Observable<?> findRefs);

	/** @param parentChange The change event on the parent that may cause the structure or one of its elements to change */
	protected abstract void onChange(ObservableConfigEvent parentChange);

	/**
	 * A {@link SettableValue} backed by an {@link ObservableConfig}
	 *
	 * @param <E> The type of the value
	 */
	static class ObservableConfigValue<E> extends ObservableConfigTransform implements SettableValue<E> {
		private final TypeToken<E> theType;
		private final ObservableConfigFormat<E> theFormat;

		private final ListenerList<Observer<? super ObservableValueEvent<E>>> theListeners;

		private E theValue;
		private final ValueHolder<E> theModifyingValue;
		private boolean isSetting;

		private Object theIdentity;
		private Object theChangesIdentity;

		ObservableConfigValue(Transactable lock, ObservableConfigParseSession session,
			ObservableValue<? extends ObservableConfig> parent, Consumer<Boolean> ceCreate, Observable<?> until, TypeToken<E> type,
			ObservableConfigFormat<E> format, boolean listen, Observable<?> findRefs) {
			super(lock, session, parent, ceCreate, until);
			theType = type;
			theFormat = format;

			theListeners = ListenerList.build().withFastSize(false).build();
			theModifyingValue = new ValueHolder<>();

			init(until == null ? Observable.empty() : until, listen, findRefs);
		}

		@Override
		protected boolean publishSelf() {
			return false; // This value doesn't have its own config--it belongs to the actual value
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
				public CoreId getCoreId() {
					return ObservableConfigValue.this.getCoreId();
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
				isSetting = true;
				getParent(true, false, parent -> {
					try (Transaction parentT = parent.lock(true, cause)) {
						E oldV = theValue;
						oldValue[0] = oldV;
						theFormat.format(getSession(), value, oldV, (__, trivial) -> parent, theModifyingValue, getUntil());
					} finally {
						theModifyingValue.clear();
					}
				});
			} finally {
				isSetting = false;
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
			if (!isSetting) {
				E oldValue = theValue;
				try {
					theValue = theFormat.parse(ObservableConfigFormat.ctxFor(getLock(), getSession(), getParent(),
						trivial -> getParent(true, trivial, null), null, getUntil(), theValue, findRefs, v -> theValue = v));
				} catch (ParseException e) {
					e.printStackTrace();
					theValue = null;
				}
				if (oldValue != theValue)
					fire(createChangeEvent(oldValue, theValue, cause));
			}
		}

		@Override
		protected void onChange(ObservableConfigEvent parentChange) {
			E oldValue = theValue;
			if (!theModifyingValue.isPresent()) {
				try {
					theValue = theFormat.parse(ObservableConfigFormat.ctxFor(getLock(), getSession(), getParent(),
						trivial -> getParent(true, trivial, null), parentChange, getUntil(), theValue, Observable.constant(null), null));
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
			try (Transaction t = event.use()) {
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

	/**
	 * Represents a collection of some kind backed by {@link ObservableConfig}
	 *
	 * @param <E> The type of the collection
	 */
	static abstract class ObservableConfigBackedCollection<E> extends ObservableConfigTransform {
		final TypeToken<E> theType;
		private final ObservableConfigFormat<E> theFormat;
		private final String theChildName;

		private final BetterSortedMap<ElementId, ConfigElement> theElements;
		private final ListenerList<Consumer<? super ObservableCollectionEvent<? extends E>>> theListeners;
		private final OCBCCollection theCollection;

		ConfigElement theNewElement;
		Consumer<? super ConfigElement> thePreAddAction;
		E theMovingValue;

		ObservableConfigBackedCollection(Transactable lock, ObservableConfigParseSession session,
			ObservableValue<? extends ObservableConfig> collectionElement, Consumer<Boolean> ceCreate, TypeToken<E> type,
			ObservableConfigFormat<E> format, String childName, Observable<?> until, boolean listen, Observable<?> findRefs) {
			super(lock, session, collectionElement, ceCreate, until);
			theType = type;
			theFormat = format;
			theChildName = childName;

			theElements = new BetterTreeMap<>(false, ElementId::compareTo);
			theListeners = ListenerList.build().allowReentrant().withFastSize(false).build();

			theCollection = createCollection();

			init(until, listen, findRefs);
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
						false, cve.get(), cve.get(), cause));
				}
			}
			if (collectionElement != null) {
				for (ObservableConfig child : collectionElement.getContent(theChildName).getValues()) {
					ConfigElement cve = createElement(child, null, findRefs);
					cve.theElement = theElements.putEntry(child.getParentChildRef(), cve, false).getElementId();
					fire(new ObservableCollectionEvent<>(cve.getElementId(), theType, theElements.size() - 1, CollectionChangeType.add,
						false, null, cve.get(), cause));
				}
			}
		}

		@Override
		protected void onChange(ObservableConfigEvent collectionChange) {
			if (collectionChange == null || collectionChange.relativePath.isEmpty()
				|| collectionChange.eventTarget != getParent(false, true, null))
				return; // Doesn't affect us
			boolean elementChange = collectionChange.relativePath.size() == 1;
			ObservableConfig config = collectionChange.relativePath.get(0);
			if (elementChange && collectionChange.changeType == CollectionChangeType.add) {
				if (!collectionChange.relativePath.getFirst().getName().equals(theChildName))
					return; // Not the right name
				ConfigElement newEl;
				if (theNewElement != null)
					newEl = theNewElement;
				else
					newEl = createElement(config, null, Observable.constant(null));
				initialize(newEl, thePreAddAction, collectionChange);
			} else {
				CollectionElement<ConfigElement> el = theElements.getEntry(config.getParentChildRef());
				if (el == null) {
					if (elementChange && collectionChange.relativePath.getFirst().getName().equals(theChildName)
						&& !collectionChange.oldName.equals(theChildName)) {
						// Renamed config to be relevant to us
						ConfigElement newEl;
						if (theNewElement != null)
							newEl = theNewElement;
						else
							newEl = createElement(config, null, Observable.constant(null));
						initialize(newEl, thePreAddAction, collectionChange);
					} else // Must be a different child
						return;
				} else if (elementChange && (//
					!collectionChange.relativePath.getFirst().getName().equals(theChildName)// Renamed to be irrelevant to us
					|| collectionChange.changeType == CollectionChangeType.remove)) {
					incrementStamp();
					theElements.mutableEntry(el.getElementId()).remove();
					el.get().dispose();
					fire(new ObservableCollectionEvent<>(el.getElementId(), theType,
						theElements.keySet().getElementsBefore(el.getElementId()), CollectionChangeType.remove, collectionChange.isMove,
						el.get().get(), el.get().get(), collectionChange));
				} else {
					try {
						E newValue;
						if (el.get().modifying != null) {
							newValue = el.get().modifying.get();
							el.get().modifying = null;
						} else {
							ObservableConfigEvent childChange = collectionChange.asFromChild();
							try (Transaction ct = childChange.use()) {
								newValue = theFormat.parse(ObservableConfigFormat.ctxFor(getLock(), getSession(), //
									ObservableValue.of(el.get().getConfig()),
									trivial -> collectionChange.eventTarget.addChild(theChildName),
									childChange, getUntil(), el.get().get(), Observable.constant(null), null));
							}
						}
						E oldValue = el.get().get();
						incrementStamp();
						if (newValue != oldValue)
							el.get()._set(newValue);
						fire(new ObservableCollectionEvent<>(el.getElementId(), theType,
							theElements.keySet().getElementsBefore(el.getElementId()), CollectionChangeType.set, false, oldValue, newValue,
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
			boolean move = cause instanceof ObservableConfigEvent && ((ObservableConfigEvent) cause).isMove;
			fire(new ObservableCollectionEvent<>(newElId, theType, theElements.keySet().getElementsBefore(newElId),
				CollectionChangeType.add, move, null, newEl.get(), cause));
		}

		private void fire(ObservableCollectionEvent<E> event) {
			try (Transaction t = event.use()) {
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
			getParent(true, true, parent -> {
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
						if (thePreAddAction != null) {
							thePreAddAction.accept(theNewElement);
							thePreAddAction = null;
						}
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
				theElementObservable = SimpleObservable.build().withLocking(theConfig).build();

				if (value != null && value.isPresent()) {
					theFormat.format(getSession(), value.get(), null, (__, trivial) -> config, v -> theValue = v,
						Observable.or(getUntil(), theElementObservable));
				} else {
					E val;

					try {
						val = theFormat.parse(ObservableConfigFormat.ctxFor(getLock(), getSession(), //
							ObservableValue.of(this.theConfig), trivial -> this.theConfig.getParent().addChild(theChildName), null,
							Observable.or(getUntil(), theElementObservable), theMovingValue, findRefs, this::_set));
						theMovingValue = null;
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
						getSession(), value, get(), (__, ___) -> theConfig, v -> {
						}, Observable.or(getUntil(), theElementObservable));
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
			public CoreId getCoreId() {
				return ObservableConfigBackedCollection.this.getCoreId();
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
			public BetterList<CollectionElement<E>> getElementsBySource(ElementId sourceEl, BetterCollection<?> sourceCollection) {
				if (sourceCollection == this)
					return BetterList.of(getElement(sourceEl));
				BetterList<CollectionElement<ConfigElement>> els = theElements.values().getElementsBySource(sourceEl, sourceCollection);
				if (!els.isEmpty())
					return QommonsUtils.map2(els, el -> el.get().immutable());
				ObservableConfig parent = getParent(false, true, null);
				if (parent == null)
					return BetterList.empty();
				BetterList<CollectionElement<ObservableConfig>> configEls = parent.getContent().getElementsBySource(sourceEl,
					sourceCollection);
				return QommonsUtils.map2(configEls, el -> theElements.get(el.getElementId()).immutable());
			}

			@Override
			public BetterList<ElementId> getSourceElements(ElementId localElement, BetterCollection<?> sourceCollection) {
				BetterCollection<ConfigElement> values = theElements.values();
				if (sourceCollection == this)
					return values.getSourceElements(localElement, values);
				return values.getSourceElements(localElement, sourceCollection);
			}

			@Override
			public ElementId getEquivalentElement(ElementId equivalentEl) {
				BetterCollection<ConfigElement> values = theElements.values();
				return values.getEquivalentElement(equivalentEl);
			}

			@Override
			public String canMove(ElementId valueEl, ElementId after, ElementId before) {
				try (Transaction t = lock(false, null)) {
					ObservableConfig valueConfig = theElements.getEntryById(valueEl).get().theConfig;
					ObservableConfig afterConfig = after == null ? null : theElements.getEntryById(after).get().theConfig;
					ObservableConfig beforeConfig = before == null ? null : theElements.getEntryById(before).get().theConfig;
					return getParent().get().canMoveChild(valueConfig, afterConfig, beforeConfig);
				}
			}

			@Override
			public CollectionElement<E> move(ElementId valueEl, ElementId after, ElementId before, boolean first, Runnable afterRemove)
				throws UnsupportedOperationException, IllegalArgumentException {
				try (Transaction t = lock(true, null)) {
					ObservableConfig valueConfig = theElements.getEntryById(valueEl).get().theConfig;
					ObservableConfig afterConfig = after == null ? null : theElements.getEntryById(after).get().theConfig;
					ObservableConfig beforeConfig = before == null ? null : theElements.getEntryById(before).get().theConfig;
					theMovingValue = getElement(valueEl).get();
					ObservableConfig newConfig = getParent().get().moveChild(valueConfig, afterConfig, beforeConfig, first, afterRemove);
					return theElements.get(newConfig.getParentChildRef()).immutable();
				}
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

		ObservableConfigValues(Transactable lock, ObservableConfigParseSession session,
			ObservableValue<? extends ObservableConfig> collectionElement, Consumer<Boolean> ceCreate, TypeToken<E> type,
			ObservableConfigFormat<E> format, String childName, Observable<?> until, boolean listen, Observable<?> findRefs) {
			theBacking = new Backing<>(lock, session, collectionElement, ceCreate, type, format, childName, until, listen, findRefs);

			init(theBacking.getCollection());
		}

		protected Backing<E> getBacking() {
			return theBacking;
		}

		protected void onChange(ObservableConfigEvent collectionChange) {
			theBacking.onChange(collectionChange);
		}

		static class Backing<E> extends ObservableConfigBackedCollection<E> {
			Backing(Transactable lock, ObservableConfigParseSession session,
				ObservableValue<? extends ObservableConfig> collectionElement, Consumer<Boolean> ceCreate, TypeToken<E> type,
				ObservableConfigFormat<E> format, String childName, Observable<?> until, boolean listen, Observable<?> findRefs) {
				super(lock, session, collectionElement, ceCreate, type, format, childName, until, listen, findRefs);
			}

			@Override
			protected OCBCCollection createCollection() {
				return new OCBCCollection() {
					@Override
					public String canAdd(E value, ElementId after, ElementId before) {
						if (!belongs(value))
							return StdMsg.ILLEGAL_ELEMENT;
						ObservableConfig parent = getParent().get();
						return parent.canAddChild(//
							after == null ? null : parent.getContent().getElement(after).get(), //
								before == null ? null : parent.getContent().getElement(before).get());
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
					return getParent().get().canRemove();
				}

				@Override
				public void remove() throws UnsupportedOperationException {
					removeOp();
				}
			}
		}
	}

	/**
	 * A config-backed {@link SyncValueSet} implementation
	 *
	 * @param <E> The type of value in the set
	 */
	static class ObservableConfigEntityValues<E> extends ObservableConfigBackedCollection<E> implements SyncValueSet<E> {
		ObservableConfigEntityValues(Transactable lock, ObservableConfigParseSession session,
			ObservableValue<? extends ObservableConfig> collectionElement, Consumer<Boolean> ceCreate, EntityConfigFormat<E> format,
			String childName, Observable<?> until, boolean listen, Observable<?> findRefs) {
			super(lock, session, collectionElement, ceCreate, format.getEntityType().getType(), format, childName, until, listen, findRefs);
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
		public ObservableCollection<E> getValues() {
			return getCollection();
		}

		@Override
		public <E2 extends E> SyncValueCreator<E, E2> create(TypeToken<E2> subType) {
			if (!isConnected().get())
				throw new UnsupportedOperationException("Not connected");
			return new SimpleValueCreator<E, E2>(getFormat().create(getSession(), subType)) {
				private ObservableConfig theTemplate;

				@Override
				public SyncValueCreator<E, E2> copy(E template) {
					theTemplate = (ObservableConfig) getFormat().getEntityType().getAssociated(template,
						EntityConfigFormat.ENTITY_CONFIG_KEY);
					return this;
				}

				@Override
				public String isEnabled(ConfiguredValueField<? super E2, ?> field) {
					return null;
				}

				@Override
				public <F> String isAcceptable(ConfiguredValueField<? super E2, F> field, F value) {
					return null;
				}

				@Override
				public String canCreate() {
					return getParent().get().canAddChild(null, null);
				}

				@Override
				public CollectionElement<E> create(Consumer<? super E2> preAddAction) {
					return add(cfg -> {
						if (theTemplate != null) {
							cfg.copyFrom(theTemplate, true);
							for (EntityConfiguredValueField<E, ?> field : getFormat().getEntityType().getFields().allValues()) {
								getFormat().getFieldFormat(field).postCopy(cfg.getChild(getFormat().getChildName(field)));
							}
						}
						return createValue(cfg, getUntil());
					}, getAfter(), getBefore(), isTowardBeginning(), element -> {
						if (preAddAction != null)
							preAddAction.accept((E2) element.get());
					});
				}
			};
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
				return null;
			}

			@Override
			public String isAcceptable(E value) {
				if (value == get())
					return null;
				return StdMsg.ILLEGAL_ELEMENT;
			}

			@Override
			public void set(E value) throws UnsupportedOperationException, IllegalArgumentException {
				if (value == get()) {
					ObservableConfig parent = getConfig().getParent();
					if (parent != null)
						((ObservableCollection<ObservableConfig>) parent.getAllContent().getValues())
						.mutableElement(getConfig().getParentChildRef()).set(getConfig());
				} else
					throw new UnsupportedOperationException(StdMsg.ILLEGAL_ELEMENT);
			}

			@Override
			public String canRemove() {
				return getParent().get().canRemove();
			}

			@Override
			public void remove() throws UnsupportedOperationException {
				removeOp();
			}
		}
	}

	/**
	 * A config-backed {@link ObservableMap} implementation
	 *
	 * @param <K> The key type of the map
	 * @param <V> The value type of the map
	 */
	static class ObservableConfigMap<K, V> implements ObservableMap<K, V> {
		private final ObservableConfigValues<MapEntry<K, V>> theCollection;
		private ObservableMap<K, V> theWrapped;

		ObservableConfigMap(Transactable lock, ObservableConfigParseSession session,
			ObservableValue<? extends ObservableConfig> collectionElement, Consumer<Boolean> ceCreate, Impl.EntryFormat<K, V> entryFormat,
			Observable<?> until, boolean listen, Observable<?> findRefs) {
			TypeToken<K> keyType = entryFormat.getKeyField().type;
			TypeToken<V> valueType = entryFormat.getValueField().type;
			theCollection = new ObservableConfigValues<>(lock, session, collectionElement, ceCreate,
				TypeTokens.get().keyFor(MapEntry.class).<MapEntry<K, V>> parameterized(keyType, valueType), //
				entryFormat, entryFormat.getValueField().childName,
				until, listen, findRefs);
			findRefs.act(__ -> {
				theWrapped = theCollection.flow()
					.groupBy(keyType, //
						LambdaUtils.printableFn(entry -> entry.key, "key", null), //
						LambdaUtils.printableBiFn((key, entry) -> {
							entry.key = key;
							return entry;
						}, "setKey", null))//
					.withValues(values -> values.transform(valueType, tx -> {
						return tx.cache(false).map(LambdaUtils.printableFn(entry -> entry.value, "value", null))//
							.modifySource(LambdaUtils.printableBiConsumer((entry, value) -> entry.value = value, () -> "setValue", null), //
								rvrs -> rvrs
								.createWith(LambdaUtils.printableFn(value -> new MapEntry<>(null, value), "createEntry", null)));
					})).gatherActive(until).singleMap(true);
			});
		}

		protected void onChange(ObservableConfigEvent change) {
			theCollection.onChange(change);
		}

		@Override
		public Object getIdentity() {
			return theWrapped.getIdentity();
		}

		@Override
		public TypeToken<K> getKeyType() {
			return theWrapped.getKeyType();
		}

		@Override
		public TypeToken<V> getValueType() {
			return theWrapped.getValueType();
		}

		@Override
		public TypeToken<java.util.Map.Entry<K, V>> getEntryType() {
			return theWrapped.getEntryType();
		}

		@Override
		public Equivalence<? super V> equivalence() {
			return theWrapped.equivalence();
		}

		@Override
		public boolean isLockSupported() {
			return theCollection.getBacking().isLockSupported();
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return theCollection.getBacking().lock(write, cause);
		}

		@Override
		public Transaction tryLock(boolean write, Object cause) {
			return theCollection.getBacking().tryLock(write, cause);
		}

		@Override
		public ObservableSet<K> keySet() {
			return theWrapped.keySet();
		}

		@Override
		public MapEntryHandle<K, V> getEntry(K key) {
			return theWrapped.getEntry(key);
		}

		@Override
		public MapEntryHandle<K, V> getOrPutEntry(K key, Function<? super K, ? extends V> value, ElementId after, ElementId before,
			boolean first, Runnable added) {
			return theWrapped.getOrPutEntry(key, value, after, before, first, added);
		}

		@Override
		public MapEntryHandle<K, V> getEntryById(ElementId entryId) {
			return theWrapped.getEntryById(entryId);
		}

		@Override
		public MutableMapEntryHandle<K, V> mutableEntry(ElementId entryId) {
			return theWrapped.mutableEntry(entryId);
		}

		@Override
		public String canPut(K key, V value) {
			if (!keySet().belongs(key))
				return StdMsg.ILLEGAL_ELEMENT;
			else if (containsKey(key))
				return StdMsg.ELEMENT_EXISTS;
			else
				return null;
		}

		@Override
		public Subscription onChange(Consumer<? super ObservableMapEvent<? extends K, ? extends V>> action) {
			return theWrapped.onChange(action);
		}

		@Override
		public int hashCode() {
			return theWrapped.hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			return theWrapped.equals(obj);
		}

		@Override
		public String toString() {
			return theWrapped.toString();
		}
	}

	/**
	 * A config-backed {@link ObservableMultiMap} implementation
	 *
	 * @param <K> The key type of the map
	 * @param <V> The value type of the map
	 */
	static class ObservableConfigMultiMap<K, V> implements ObservableMultiMap<K, V> {
		private final ObservableConfigValues<MapEntry<K, V>> theCollection;
		private ObservableMultiMap<K, V> theWrapped;

		ObservableConfigMultiMap(Transactable lock, ObservableConfigParseSession session,
			ObservableValue<? extends ObservableConfig> collectionElement, Consumer<Boolean> ceCreate, Impl.EntryFormat<K, V> entryFormat,
			Observable<?> until, boolean listen, Observable<?> findRefs) {
			TypeToken<K> keyType = entryFormat.getKeyField().type;
			TypeToken<V> valueType = entryFormat.getValueField().type;
			theCollection = new ObservableConfigValues<>(lock, session, collectionElement, ceCreate,
				TypeTokens.get().keyFor(MapEntry.class).<MapEntry<K, V>> parameterized(keyType, valueType), //
				entryFormat, entryFormat.getValueField().childName, until, listen, findRefs);
			findRefs.act(__ -> {
				theWrapped = theCollection.flow()
					.groupBy(keyType, //
						LambdaUtils.printableFn(entry -> entry.key, "key", null), //
						LambdaUtils.printableBiFn((key, entry) -> {
							entry.key = key;
							return entry;
						}, "setKey", null))//
					.withValues(values -> values.transform(valueType, tx -> {
						return tx.cache(false).map(LambdaUtils.printableFn(entry -> entry.value, "value", null))//
							.modifySource(LambdaUtils.printableBiConsumer((entry, value) -> entry.value = value, () -> "setValue", null), //
								rvrs -> rvrs
								.createWith(LambdaUtils.printableFn(value -> new MapEntry<>(null, value), "createEntry", null)));
					})).gatherActive(until);
			});
		}

		protected void onChange(ObservableConfigEvent change) {
			theCollection.onChange(change);
		}

		@Override
		public Object getIdentity() {
			return theWrapped.getIdentity();
		}

		@Override
		public boolean isLockSupported() {
			return theCollection.getBacking().isLockSupported();
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return theCollection.getBacking().lock(write, cause);
		}

		@Override
		public Transaction tryLock(boolean write, Object cause) {
			return theCollection.getBacking().tryLock(write, cause);
		}

		@Override
		public CoreId getCoreId() {
			return theCollection.getBacking().getCoreId();
		}

		@Override
		public long getStamp() {
			return theWrapped.getStamp();
		}

		@Override
		public TypeToken<K> getKeyType() {
			return theWrapped.getKeyType();
		}

		@Override
		public TypeToken<V> getValueType() {
			return theWrapped.getValueType();
		}

		@Override
		public TypeToken<MultiEntryHandle<K, V>> getEntryType() {
			return theWrapped.getEntryType();
		}

		@Override
		public TypeToken<MultiEntryValueHandle<K, V>> getEntryValueType() {
			return theWrapped.getEntryValueType();
		}

		@Override
		public ObservableSet<K> keySet() {
			return theWrapped.keySet();
		}

		@Override
		public MultiEntryHandle<K, V> getEntryById(ElementId keyId) {
			return theWrapped.getEntryById(keyId);
		}

		@Override
		public MultiEntryHandle<K, V> getOrPutEntry(K key, Function<? super K, ? extends Iterable<? extends V>> value, ElementId afterKey,
			ElementId beforeKey, boolean first, Runnable added) {
			return theWrapped.getOrPutEntry(key, value, afterKey, beforeKey, first, added);
		}

		@Override
		public int valueSize() {
			return theWrapped.valueSize();
		}

		@Override
		public boolean clear() {
			return theWrapped.clear();
		}

		@Override
		public ObservableMultiEntry<K, V> watchById(ElementId keyId) {
			return theWrapped.watchById(keyId);
		}

		@Override
		public ObservableMultiEntry<K, V> watch(K key) {
			return theWrapped.watch(key);
		}

		@Override
		public Subscription onChange(Consumer<? super ObservableMultiMapEvent<? extends K, ? extends V>> action) {
			return theWrapped.onChange(action);
		}

		@Override
		public MultiMapFlow<K, V> flow() {
			return theWrapped.flow();
		}

		@Override
		public int hashCode() {
			return theWrapped.hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			return theWrapped.equals(obj);
		}

		@Override
		public String toString() {
			return theWrapped.toString();
		}
	}
}
