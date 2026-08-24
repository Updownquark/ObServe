package org.observe.config;

import java.lang.reflect.Proxy;
import java.text.ParseException;
import java.util.Collection;
import java.util.Iterator;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

import org.observe.Equivalence;
import org.observe.Eventable;
import org.observe.Observable;
import org.observe.Observable.CoreChangeSources;
import org.observe.ObservableValue;
import org.observe.ObservableValueEvent;
import org.observe.Observer;
import org.observe.SettableValue;
import org.observe.SettableValueListening;
import org.observe.SimpleObservable;
import org.observe.assoc.ObservableMap;
import org.observe.assoc.ObservableMapEvent;
import org.observe.assoc.ObservableMultiMap;
import org.observe.assoc.ObservableMultiMapEvent;
import org.observe.collect.CollectionChangeType;
import org.observe.collect.CollectionElementMove;
import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableCollectionEvent;
import org.observe.collect.ObservableSet;
import org.observe.config.EntityConfiguredValueType.EntityConfiguredValueField;
import org.observe.config.ObservableConfig.ObservableConfigEvent;
import org.observe.config.ObservableConfigFormat.EntityConfigFormat;
import org.observe.config.ObservableConfigFormat.Impl;
import org.observe.config.ObservableConfigFormat.MapEntry;
import org.observe.util.ObservableCollectionWrapper;
import org.qommons.CausalLock;
import org.qommons.Identifiable;
import org.qommons.Identifiable.AbstractIdentifiable;
import org.qommons.QommonsUtils;
import org.qommons.Stamped;
import org.qommons.Subscription;
import org.qommons.ThreadConstraint;
import org.qommons.Transaction;
import org.qommons.ValueHolder;
import org.qommons.collect.*;
import org.qommons.collect.MutableCollectionElement.StdMsg;
import org.qommons.fn.FunctionUtils;
import org.qommons.tree.BetterTreeMap;

import com.google.common.reflect.TypeToken;

/** A super class for observable structures backed by an {@link ObservableConfig} */
public abstract class ObservableConfigTransform extends AbstractIdentifiable implements CausalLock, Stamped, Eventable {
	private final CausalLock theLock;
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
	protected ObservableConfigTransform(CausalLock lock, ObservableConfigParseSession session,
		ObservableValue<? extends ObservableConfig> parent, Consumer<Boolean> ceCreate, Observable<?> until) {
		theLock = lock;
		theSession = session;
		theParent = parent;
		theParentCreate = ceCreate;
		if (until == null) {
			isConnected = ObservableValue.of(true);
			theUntil = theParent.noInitChanges().filter(evt -> evt.getOldValue() != evt.getNewValue());
		} else {
			isConnected = ObservableValue.of(() -> _isConnected, until.take(1));
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
				try (Transaction ceT = newParent == null ? Transaction.NONE : newParent.lock(false)) {
					initConfig(evt.getNewValue(), evt, initialized[0] ? Observable.constant(null) : findRefs);
					if (listen && newParent != null)
						newParent.watch("").takeUntil(theUntil).act(Observer.printableObserver(this::onChange, //
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
	protected CausalLock getLock() {
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
		Transaction parentLock = createIfAbsent ? lockWrite(false, null) : lock(false);
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
	public ThreadConstraint getThreadConstraint() {
		return theLock.getThreadConstraint();
	}

	@Override
	public boolean isEventing() {
		ObservableConfig config = theParent.get();
		return config != null && config.isEventing();
	}

	@Override
	public Transaction lock(boolean tryOnly) {
		return theLock.lock(tryOnly);
	}

	@Override
	public Transaction lockWrite(boolean tryOnly, Object cause) {
		return theLock.lockWrite(tryOnly, cause);
	}

	@Override
	public Collection<Cause> getCurrentCauses() {
		return theLock.getCurrentCauses();
	}

	@Override
	public CoreId getCoreId() {
		return theLock.getCoreId();
	}

	/** @return Changes sources affecting this transform */
	protected Observable.CoreChangeSources getChangeSources() {
		ObservableConfig parent = theParent.get();
		if (parent == null)
			return theParent.noInitChanges().getChangeSources();
		else
			return parent.getChangeSources().union(theParent.noInitChanges().getChangeSources());
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
		private final ObservableConfigFormat<E> theFormat;

		private final SettableValueListening<ObservableValueEvent<E>> theListeners;

		private E theValue;
		private final ValueHolder<E> theModifyingValue;
		private boolean isSetting;

		ObservableConfigValue(CausalLock lock, ObservableConfigParseSession session, ObservableValue<? extends ObservableConfig> parent,
			Consumer<Boolean> ceCreate, Observable<?> until, ObservableConfigFormat<E> format, boolean listen, Observable<?> findRefs) {
			super(lock, session, parent, ceCreate, until);
			theFormat = format;

			theListeners = new SettableValueListening<>(null, null,
				ListenerList.build().withFastSize(false).skipAddByDefault(true).build());
			theModifyingValue = new ValueHolder<>();

			init(until == null ? Observable.empty() : until, listen, findRefs);
		}

		@Override
		protected boolean publishSelf() {
			return false; // This value doesn't have its own config--it belongs to the actual value
		}

		@Override
		protected Object createIdentity() {
			return Identifiable.wrap(getParent().getIdentity(), "value", theFormat);
		}

		@Override
		public ObservableConfigValue<E> alias(String alias) {
			super.alias(alias);
			return this;
		}

		@Override
		public Getter<E> lock(boolean tryOnly) {
			Transaction lock = super.lock(tryOnly);
			if (lock == null)
				return null;
			return new Getter<E>() {
				@Override
				public E get() {
					return theValue;
				}

				@Override
				public void close() {
					lock.close();
				}
			};
		}

		@Override
		public Setter<E> lockWrite(boolean tryOnly, Object cause) {
			Transaction superLock = super.lockWrite(tryOnly, cause);
			if (superLock == null)
				return null;
			Transaction listenerLock = theListeners.lockWrite(true, cause);
			if (listenerLock == null) {
				if (tryOnly) {
					superLock.close();
					return null;
				}
				do {
					superLock.close();
					superLock = super.lockWrite(false, cause);
					listenerLock = theListeners.lockWrite(true, cause);
				} while (listenerLock == null);
			}
			Transaction fSuperLock = superLock;
			Transaction fListenerLock = listenerLock;
			Getter<Boolean> connected = isConnected().lock(false);
			return new Setter<E>() {
				@Override
				public E get() {
					return theValue;
				}

				@Override
				public String isEnabled() {
					if (connected.get())
						return null;
					else
						return "Not connected";
				}

				@Override
				public String isAcceptable(E value) {
					return isEnabled();
				}

				@Override
				public E set(E value) {
					isSetting = true;
					Object[] oldValue = new Object[1];
					boolean[] changed = new boolean[1];
					try {
						getParent(true, false, parent -> {
							try (Transaction parentT = parent.lockWrite(false, null)) {
								E oldV = theValue;
								oldValue[0] = oldV;
								changed[0] = theFormat.format(getSession(), value, oldV, (__, trivial) -> parent, theModifyingValue, false,
									getUntil());
							} finally {
								theModifyingValue.clear();
							}
						});
						if (!changed[0]) {// If there was no change by the format, we need to fire an event ourselves
							ObservableValueEvent<E> evt = createChangeEvent((E) oldValue[0], value, getCurrentCauses());
							try (Transaction t = evt.use()) {
								theListeners.fire(evt);
							}
						}
					} finally {
						isSetting = false;
					}
					return (E) oldValue[0];
				}

				@Override
				public void close() {
					connected.close();
					fListenerLock.close();
					fSuperLock.close();
				}
			};
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
					return Identifiable.wrap(ObservableConfigValue.this.getIdentity(), "noInitChanges");
				}

				@Override
				public ThreadConstraint getThreadConstraint() {
					return ObservableConfigValue.this.getThreadConstraint();
				}

				@Override
				public boolean isEventing() {
					return ObservableConfigValue.this.isEventing();
				}

				@Override
				public Transaction lock(boolean tryOnly) {
					return ObservableConfigValue.this.lock(tryOnly);
				}

				@Override
				public CoreId getCoreId() {
					return ObservableConfigValue.this.getCoreId();
				}

				@Override
				public long getStamp() {
					return ObservableConfigValue.this.getStamp();
				}

				@Override
				public CoreChangeSources getChangeSources() {
					return ObservableConfigValue.this.getChangeSources();
				}

				@Override
				public Subscription subscribe(Observer<? super ObservableValueEvent<E>> observer) {
					return theListeners.subscribe(observer);
				}
			}
			return new OCVChanges();
		}

		@Override
		public CoreChangeSources getChangeSources() {
			return super.getChangeSources();
		}

		@Override
		public E set(E value) throws IllegalArgumentException, UnsupportedOperationException {
			if (!isConnected().get())
				throw new UnsupportedOperationException("Not connected");
			Object[] oldValue = new Object[1];
			try (Transaction t = lockWrite(false, null)) {
				isSetting = true;
				boolean[] changed = new boolean[1];
				getParent(true, false, parent -> {
					try (Transaction parentT = parent.lockWrite(false, null)) {
						E oldV = theValue;
						oldValue[0] = oldV;
						changed[0] = theFormat.format(getSession(), value, oldV, (__, trivial) -> parent, theModifyingValue, false,
							getUntil());
					} finally {
						theModifyingValue.clear();
					}
				});
				if (!changed[0]) // If there was no change by the format, we need to fire an event ourselves
					fire(createChangeEvent((E) oldValue[0], value, getCurrentCauses()));
			} finally {
				isSetting = false;
			}
			return (E) oldValue[0];
		}

		@Override
		public String isAcceptable(E value) {
			if (!isConnected().get())
				return "Not connected";
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
			incrementStamp();
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
				theListeners.fire(event);
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
		private final ObservableConfigFormat<E> theFormat;
		private final String theChildName;

		private final BetterSortedMap<ElementId, ConfigElement> theElements;
		private final ListenerList<Consumer<? super ObservableCollectionEvent<? extends E>>> theListeners;
		private final OCBCCollection theCollection;

		ConfigElement theNewElement;
		Consumer<? super ConfigElement> thePreAddAction;
		E theMovingValue;

		ObservableConfigBackedCollection(CausalLock lock, ObservableConfigParseSession session,
			ObservableValue<? extends ObservableConfig> collectionElement, Consumer<Boolean> ceCreate, ObservableConfigFormat<E> format,
			String childName, Observable<?> until, boolean listen, Observable<?> findRefs) {
			super(lock, session, collectionElement, ceCreate, until);
			theFormat = format;
			theChildName = childName;

			theElements = BetterTreeMap.<ElementId> build(ElementId::compareTo).buildMap();
			theListeners = ListenerList.build().allowReentrant().withFastSize(false).build();

			theCollection = createCollection();

			init(until, listen, findRefs);
		}

		@Override
		protected Object createIdentity() {
			return Identifiable.wrap(getParent().getIdentity(), theChildName, theFormat);
		}

		@Override
		public ObservableConfigBackedCollection<E> alias(String alias) {
			super.alias(alias);
			return this;
		}

		@Override
		protected void initConfig(ObservableConfig collectionElement, Object cause, Observable<?> findRefs) {
			if (!theElements.isEmpty()) {
				Iterator<ConfigElement> cveIter = theElements.values().reversed().iterator();
				while (cveIter.hasNext()) {
					ConfigElement cve = cveIter.next();
					cveIter.remove();
					cve.dispose();
					fire(ObservableCollectionEvent.createCollectionEvent(cve.getElementId(), theElements.size(),
						CollectionChangeType.remove, cve.get(), cve.get(), cause));
				}
			}
			if (collectionElement != null) {
				for (ObservableConfig child : collectionElement.getContent()) {
					if (!isChild(child))
						continue;
					ConfigElement cve = createElement(child, null, findRefs);
					cve.theElement = theElements.putEntry(child.getParentChildRef().getElementId(), cve, false);
					fire(ObservableCollectionEvent.createCollectionEvent(cve.getElementId(), theElements.size() - 1,
						CollectionChangeType.add, null, cve.get(), cause));
				}
			}
		}

		private boolean isChild(ObservableConfig config) {
			return theChildName.equals(config.getName())//
				|| (theFormat instanceof ObservableConfigFormat.HeterogeneousConfigFormat //
					&& ((ObservableConfigFormat.HeterogeneousConfigFormat<E>) theFormat).isRecognized(config));
		}

		@Override
		protected void onChange(ObservableConfigEvent collectionChange) {
			if (collectionChange == null || collectionChange.relativePath.isEmpty()
				|| collectionChange.eventTarget != getParent(false, true, null))
				return; // Doesn't affect us
			boolean elementChange = collectionChange.relativePath.size() == 1;
			ObservableConfig config = collectionChange.relativePath.get(0);
			boolean relevant = isChild(collectionChange.relativePath.getFirst());
			if (elementChange && collectionChange.changeType == CollectionChangeType.add) {
				if (!relevant)
					return; // Not my baby
				ConfigElement newEl;
				if (theNewElement != null)
					newEl = theNewElement;
				else
					newEl = createElement(config, null, Observable.constant(null));
				initialize(newEl, thePreAddAction, collectionChange);
			} else {
				ListElement<ConfigElement> el = theElements.getEntry(config.getParentChildRef().getElementId());
				if (el == null) {
					if (relevant) {
						// Modified config to be relevant to us
						ConfigElement newEl;
						if (theNewElement != null)
							newEl = theNewElement;
						else
							newEl = createElement(config, null, Observable.constant(null));
						initialize(newEl, thePreAddAction, collectionChange);
					} else // Must be a different child
						return;
				} else if (!relevant // Modified to be irrelevant to us
					|| (elementChange && collectionChange.changeType == CollectionChangeType.remove)) {
					incrementStamp();
					theElements.mutableEntry(el.getElementId()).remove();
					el.get().dispose();
					fire(ObservableCollectionEvent.createCollectionEvent(el.getElementId(), el.getElementsBefore(),
						CollectionChangeType.remove, el.get().get(), el.get().get(), collectionChange, collectionChange.movement));
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
									trivial -> collectionChange.eventTarget.addChild(theChildName), childChange, getUntil(), el.get().get(),
									Observable.constant(null), null));
							}
						}
						E oldValue = el.get().get();
						incrementStamp();
						if (newValue != oldValue)
							el.get()._set(newValue);
						fire(ObservableCollectionEvent.createCollectionEvent(el.getElementId(), el.getElementsBefore(),
							CollectionChangeType.set, oldValue, newValue, collectionChange));
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
			OrderedMapEntry<ElementId, ConfigElement> newElId;
			ObservableConfig config = newEl.getConfig();
			CollectionElement<ElementId> el = theElements.keySet().search(config.getParentChildRef().getElementId(),
				BetterSortedList.SortedSearchFilter.PreferLess);
			if (el == null)// Must be empty
				newElId = theElements.putEntry(config.getParentChildRef().getElementId(), newEl, false);
			else if (el.get().compareTo(config.getParentChildRef().getElementId()) < 0)
				newElId = theElements.putEntry(config.getParentChildRef().getElementId(), newEl, el.getElementId(), null, true);
			else
				newElId = theElements.putEntry(config.getParentChildRef().getElementId(), newEl, null, el.getElementId(), false);
			newEl.theElement = newElId;
			incrementStamp();
			CollectionElementMove move = cause instanceof ObservableConfigEvent ? ((ObservableConfigEvent) cause).movement : null;
			fire(ObservableCollectionEvent.createCollectionEvent(newElId.getElementId(), newElId.getElementsBefore(),
				CollectionChangeType.add, null, newEl.get(), cause, move));
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
			return theListeners.add(listener, true);
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
				try (Transaction t = parent.lockWrite(false, null)) {
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

		protected abstract class ConfigElement implements MutableListElement<E> {
			private final ObservableConfig theConfig;
			private final SimpleObservable<Void> theElementObservable;
			private OrderedMapEntry<ElementId, ConfigElement> theElement;
			private E theValue;
			private ListElement<E> immutable;
			ValueHolder<E> modifying;

			protected ConfigElement(ObservableConfig config, ValueHolder<E> value, Observable<?> findRefs) {
				this.theConfig = config;
				theElementObservable = SimpleObservable.build().withLocking(theConfig).build();

				if (value != null && value.isPresent()) {
					theFormat.format(getSession(), value.get(), null, (__, trivial) -> config, v -> theValue = v, false,
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
			public ElementId getElementId() {
				return theElement.getElementId();
			}

			@Override
			public E get() {
				return theValue;
			}

			@Override
			public int getElementsBefore() {
				return theConfig.getIndexInParent();
			}

			@Override
			public int getElementsAfter() {
				ObservableConfig parent = theConfig.getParent();
				if (parent == null)
					return 0;
				return theConfig.getParentChildRef().getElementsAfter();
			}

			@Override
			public ConfigElement getAdjacent(boolean next) {
				OrderedMapEntry<ElementId, ConfigElement> adj = theElement.getAdjacent(next);
				return adj == null ? null : adj.get();
			}

			protected void _set(E value) {
				theValue = value;
			}

			protected void setOp(E value) throws UnsupportedOperationException, IllegalArgumentException {
				if (!isConnected().get())
					throw new UnsupportedOperationException("Not connected");
				try (Transaction t = lockWrite(false, null)) {
					if (!theConfig.getParentChildRef().getElementId().isPresent())
						throw new IllegalArgumentException(StdMsg.ELEMENT_REMOVED);
					modifying = new ValueHolder<>(value);
					theFormat.format(//
						getSession(), value, get(), (__, ___) -> theConfig, FunctionUtils.consumeDoNothing(), false,
						Observable.or(getUntil(), theElementObservable));
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
			public ListElement<E> immutable() {
				if (immutable == null) {
					immutable = new ListElement<E>() {
						@Override
						public ElementId getElementId() {
							return theElement.getElementId();
						}

						@Override
						public E get() {
							return theValue;
						}

						@Override
						public ListElement<E> getAdjacent(boolean next) {
							ConfigElement adj = ConfigElement.this.getAdjacent(next);
							return adj == null ? null : adj.immutable();
						}

						@Override
						public int getElementsBefore() {
							return theElement.getElementsBefore();
						}

						@Override
						public int getElementsAfter() {
							return theElement.getElementsAfter();
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
				return new StringBuilder().append('[').append(theElement.getElementsBefore()).append("]=").append(theValue).toString();
			}
		}

		protected abstract class OCBCCollection extends AbstractIdentifiable implements ObservableCollection<E> {
			@Override
			protected Object createIdentity() {
				return Identifiable.wrap(getParent().getIdentity(), "values", theFormat, theChildName);
			}

			@Override
			public OCBCCollection alias(String alias) {
				super.alias(alias);
				return this;
			}

			@Override
			public long getStamp() {
				return ObservableConfigBackedCollection.this.getStamp();
			}

			@Override
			public ThreadConstraint getThreadConstraint() {
				return ObservableConfigBackedCollection.this.getThreadConstraint();
			}

			@Override
			public boolean isEventing() {
				return ObservableConfigBackedCollection.this.isEventing();
			}

			@Override
			public Transaction lock(boolean tryOnly) {
				return ObservableConfigBackedCollection.this.lock(tryOnly);
			}

			@Override
			public Transaction lockWrite(boolean tryOnly, Object cause) {
				return ObservableConfigBackedCollection.this.lockWrite(tryOnly, cause);
			}

			@Override
			public Collection<Cause> getCurrentCauses() {
				return ObservableConfigBackedCollection.this.getCurrentCauses();
			}

			@Override
			public CoreId getCoreId() {
				return ObservableConfigBackedCollection.this.getCoreId();
			}

			@Override
			public CoreChangeSources getChangeSources() {
				return ObservableConfigBackedCollection.this.getChangeSources();
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
			public ListElement<E> getElement(int index) {
				try (Transaction t = lock(false)) {
					return theElements.getEntryById(theElements.keySet().getElement(index).getElementId()).get();
				}
			}

			@Override
			public ListElement<E> getElement(E value, boolean first) {
				try (Transaction t = lock(false)) {
					ListElement<E> el = getTerminalElement(first);
					while (el != null && !Objects.equals(el.get(), value))
						el = el.getAdjacent(first);
					return el;
				}
			}

			@Override
			public ListElement<E> getElement(ElementId id) {
				return theElements.getEntryById(id).get().immutable();
			}

			@Override
			public ListElement<E> getTerminalElement(boolean first) {
				ListElement<ConfigElement> el = theElements.getTerminalEntry(first);
				return el == null ? null : el.get().immutable();
			}

			@Override
			public MutableListElement<E> mutableElement(ElementId id) {
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
				try (Transaction t = lock(false)) {
					ObservableConfig valueConfig = theElements.getEntryById(valueEl).get().theConfig;
					ObservableConfig afterConfig = after == null ? null : theElements.getEntryById(after).get().theConfig;
					ObservableConfig beforeConfig = before == null ? null : theElements.getEntryById(before).get().theConfig;
					return getParent().get().canMoveChild(valueConfig, afterConfig, beforeConfig);
				}
			}

			@Override
			public ListElement<E> move(ElementId valueEl, ElementId after, ElementId before, boolean first, Runnable afterRemove)
				throws UnsupportedOperationException, IllegalArgumentException {
				try (Transaction t = lockWrite(false, null)) {
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
				try (Transaction t = lockWrite(false, null)) {
					for (CollectionElement<ConfigElement> el : theElements.values().reversed().elements()) {
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
				try (Transaction t = lockWrite(false, null)) {
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

		ObservableConfigValues(CausalLock lock, ObservableConfigParseSession session,
			ObservableValue<? extends ObservableConfig> collectionElement, Consumer<Boolean> ceCreate, ObservableConfigFormat<E> format,
			String childName, Observable<?> until, boolean listen, Observable<?> findRefs) {
			theBacking = new Backing<>(lock, session, collectionElement, ceCreate, format, childName, until, listen, findRefs);

			init(theBacking.getCollection());
		}

		protected Backing<E> getBacking() {
			return theBacking;
		}

		protected void onChange(ObservableConfigEvent collectionChange) {
			theBacking.onChange(collectionChange);
		}

		static class Backing<E> extends ObservableConfigBackedCollection<E> {
			Backing(CausalLock lock, ObservableConfigParseSession session, ObservableValue<? extends ObservableConfig> collectionElement,
				Consumer<Boolean> ceCreate, ObservableConfigFormat<E> format, String childName, Observable<?> until, boolean listen,
				Observable<?> findRefs) {
				super(lock, session, collectionElement, ceCreate, format, childName, until, listen, findRefs);
			}

			@Override
			protected OCBCCollection createCollection() {
				return new OCBCCollection() {
					@Override
					public String canAdd(E value, ElementId after, ElementId before) {
						ObservableConfig parent = getParent().get();
						return parent.canAddChild(//
							after == null ? null : parent.getContent().getElement(after).get(), //
								before == null ? null : parent.getContent().getElement(before).get());
					}

					@Override
					public ListElement<E> addElement(E value, ElementId after, ElementId before, boolean first)
						throws UnsupportedOperationException, IllegalArgumentException {
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
		ObservableConfigEntityValues(CausalLock lock, ObservableConfigParseSession session,
			ObservableValue<? extends ObservableConfig> collectionElement, Consumer<Boolean> ceCreate, EntityConfigFormat<E> format,
			String childName, Observable<?> until, boolean listen, Observable<?> findRefs) {
			super(lock, session, collectionElement, ceCreate, format, childName, until, listen, findRefs);
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
		public <E2 extends E> SimpleValueCreator<E, E2> create(TypeToken<E2> subType) {
			if (!isConnected().get())
				throw new UnsupportedOperationException("Not connected");
			return new SimpleValueCreator<E, E2>(getFormat().create(getSession(), subType)) {
				private ObservableConfig theTemplate;

				@Override
				public SimpleValueCreator<E, E2> copy(E template) {
					if (Proxy.isProxyClass(template.getClass())) {
						theTemplate = (ObservableConfig) getFormat().getEntityType().getAssociated(template,
							EntityConfigFormat.ENTITY_CONFIG_KEY);
					} else {
						super.copy(template);
					}
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
			public ListElement<E> addElement(E value, ElementId after, ElementId before, boolean first)
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
						.mutableElement(getConfig().getParentChildRef().getElementId()).set(getConfig());
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

		ObservableConfigMap(CausalLock lock, ObservableConfigParseSession session,
			ObservableValue<? extends ObservableConfig> collectionElement, Consumer<Boolean> ceCreate, Impl.EntryFormat<K, V> entryFormat,
			Observable<?> until, boolean listen, Observable<?> findRefs) {
			theCollection = new ObservableConfigValues<>(lock, session, collectionElement, ceCreate, entryFormat,
				entryFormat.getValueField().childName, until, listen, findRefs);
			findRefs.act(__ -> {
				theWrapped = theCollection.flow().<K> groupBy(FunctionUtils.printableFn(entry -> entry.key, "key", null), //
					FunctionUtils.printableBiFn((key, entry) -> {
						entry.key = key;
						return entry;
					}, "setKey", null))//
					.withValues(values -> values.<V> transform(tx -> {
						return tx.cache(false).map(FunctionUtils.printableFn(entry -> entry.value, "value", null))//
							.modifySource(FunctionUtils.printableBiConsumer((entry, value) -> entry.value = value, () -> "setValue", null), //
								rvrs -> rvrs
								.createWith(FunctionUtils.printableFn(value -> new MapEntry<>(null, value), "createEntry", null)));
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
		public ObservableConfigMap<K, V> alias(String alias) {
			theWrapped.alias(alias);
			return this;
		}

		@Override
		public Set<String> getAliases() {
			return theWrapped.getAliases();
		}

		@Override
		public Equivalence<? super V> equivalence() {
			return theWrapped.equivalence();
		}

		@Override
		public boolean isEventing() {
			return theCollection.isEventing();
		}

		@Override
		public CoreChangeSources getChangeSources() {
			return theCollection.getChangeSources();
		}

		@Override
		public Transaction lock(boolean tryOnly) {
			return theCollection.getBacking().lock(tryOnly);
		}

		@Override
		public Transaction lockWrite(boolean tryOnly, Object cause) {
			return theCollection.getBacking().lockWrite(tryOnly, cause);
		}

		@Override
		public ObservableSet<K> keySet() {
			return theWrapped.keySet();
		}

		@Override
		public OrderedMapEntry<K, V> getEntry(K key) {
			return theWrapped.getEntry(key);
		}

		@Override
		public OrderedMapEntry<K, V> getOrPutEntry(K key, Function<? super K, ? extends V> value, ElementId after, ElementId before,
			boolean first, Runnable preAdd, Runnable postAdd) {
			return theWrapped.getOrPutEntry(key, value, after, before, first, preAdd, postAdd);
		}

		@Override
		public OrderedMapEntry<K, V> getEntryById(ElementId entryId) {
			return theWrapped.getEntryById(entryId);
		}

		@Override
		public MutableOrderedMapEntry<K, V> mutableEntry(ElementId entryId) {
			return theWrapped.mutableEntry(entryId);
		}

		@Override
		public String canPut(K key, V value) {
			if (containsKey(key))
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

		ObservableConfigMultiMap(CausalLock lock, ObservableConfigParseSession session,
			ObservableValue<? extends ObservableConfig> collectionElement, Consumer<Boolean> ceCreate, Impl.EntryFormat<K, V> entryFormat,
			Observable<?> until, boolean listen, Observable<?> findRefs) {
			theCollection = new ObservableConfigValues<>(lock, session, collectionElement, ceCreate, entryFormat,
				entryFormat.getValueField().childName, until, listen, findRefs);
			findRefs.act(__ -> {
				theWrapped = theCollection.flow().<K> groupBy(FunctionUtils.printableFn(entry -> entry.key, "key", null), //
					FunctionUtils.printableBiFn((key, entry) -> {
						entry.key = key;
						return entry;
					}, "setKey", null))//
					.withValues(values -> values.<V> transform(tx -> {
						return tx.cache(false).map(FunctionUtils.printableFn(entry -> entry.value, "value", null))//
							.modifySource(FunctionUtils.printableBiConsumer((entry, value) -> entry.value = value, () -> "setValue", null), //
								rvrs -> rvrs
								.createWith(FunctionUtils.printableFn(value -> new MapEntry<>(null, value), "createEntry", null)));
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
		public ObservableConfigMultiMap<K, V> alias(String alias) {
			theWrapped.alias(alias);
			return this;
		}

		@Override
		public Set<String> getAliases() {
			return theWrapped.getAliases();
		}

		@Override
		public ThreadConstraint getThreadConstraint() {
			return theCollection.getBacking().getThreadConstraint();
		}

		@Override
		public boolean isEventing() {
			return theCollection.isEventing();
		}

		@Override
		public Transaction lock(boolean tryOnly) {
			return theCollection.getBacking().lock(tryOnly);
		}

		@Override
		public Transaction lockWrite(boolean tryOnly, Object cause) {
			return theCollection.getBacking().lockWrite(tryOnly, cause);
		}

		@Override
		public Collection<Cause> getCurrentCauses() {
			return theCollection.getBacking().getCurrentCauses();
		}

		@Override
		public CoreId getCoreId() {
			return theCollection.getBacking().getCoreId();
		}

		@Override
		public CoreChangeSources getChangeSources() {
			return theCollection.getBacking().getChangeSources();
		}

		@Override
		public long getStamp() {
			return theWrapped.getStamp();
		}

		@Override
		public ObservableSet<K> keySet() {
			return theWrapped.keySet();
		}

		@Override
		public OrderedMultiEntry<K, V> getEntryById(ElementId keyId) {
			return theWrapped.getEntryById(keyId);
		}

		@Override
		public OrderedMultiEntry<K, V> getOrPutEntry(K key, Function<? super K, ? extends Iterable<? extends V>> value, ElementId afterKey,
			ElementId beforeKey, boolean first, Runnable preAdd, Runnable postAdd) {
			return theWrapped.getOrPutEntry(key, value, afterKey, beforeKey, first, preAdd, postAdd);
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
