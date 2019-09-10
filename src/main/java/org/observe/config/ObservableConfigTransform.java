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
import org.qommons.Lockable;
import org.qommons.StructuredTransactable;
import org.qommons.Transaction;
import org.qommons.ValueHolder;
import org.qommons.collect.BetterCollection;
import org.qommons.collect.BetterSortedMap;
import org.qommons.collect.BetterSortedSet.SortedSearchFilter;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.ElementId;
import org.qommons.collect.ListenerList;
import org.qommons.collect.MutableCollectionElement;
import org.qommons.collect.MutableCollectionElement.StdMsg;
import org.qommons.tree.BetterTreeMap;

import com.google.common.reflect.TypeToken;

public abstract class ObservableConfigTransform implements StructuredTransactable {
	private final ObservableValue<? extends ObservableConfig> theParent;
	private final Runnable theParentCreate;
	private final Observable<?> theUntil;

	private long theStamp;
	private long theStructureStamp;

	public ObservableConfigTransform(ObservableValue<? extends ObservableConfig> parent, Runnable ceCreate, Observable<?> until) {
		theParent = parent;
		theParentCreate = ceCreate;
		if (until == null)
			theUntil = theParent.noInitChanges().filter(evt -> evt.getOldValue() != evt.getNewValue());
		else
			theUntil = Observable.or(until, //
				theParent.noInitChanges().takeUntil(until).filter(evt -> evt.getOldValue() != evt.getNewValue()));
	}

	protected void init(Observable<?> until, boolean listen) {
		theParent.changes().takeUntil(until).act(//
			evt -> {
				if (evt.getNewValue() == evt.getOldValue())
					return;
				theStamp++;
				theStructureStamp++;
				ObservableConfig newParent = evt.getNewValue();
				try (Transaction ceT = newParent == null ? Transaction.NONE : newParent.lock(false, null)) {
					initConfig(evt.getNewValue(), evt, listen);
					if (listen)
						newParent.watch("").takeUntil(theUntil).act(this::onChange);
				}
			});
	}

	protected void incrementStamp(boolean structural) {
		theStamp++;
		if (structural)
			theStructureStamp++;
	}

	protected ObservableConfig getParent(boolean createIfAbsent, Consumer<ObservableConfig> parentAction) {
		if (!createIfAbsent && parentAction == null) {
			return theParent.get();
		}
		Transaction parentLock = theParent.lock();
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
	public Transaction lock(boolean write, boolean structural, Object cause) {
		return Lockable.lock(theParent, () -> Lockable.lockable(theParent.get(), write, structural, cause));
	}

	@Override
	public Transaction tryLock(boolean write, boolean structural, Object cause) {
		return Lockable.tryLock(theParent, () -> Lockable.lockable(theParent.get(), write, structural, cause));
	}

	public long getStamp(boolean structuralOnly) {
		return structuralOnly ? theStructureStamp : theStamp;
	}

	protected abstract void initConfig(ObservableConfig parent, Object cause, boolean listen);

	protected abstract void onChange(ObservableConfigEvent parentChange);

	static class ObservableConfigValue<E> extends ObservableConfigTransform implements SettableValue<E> {
		private final TypeToken<E> theType;
		private final ObservableConfigFormat<E> theFormat;

		private final ListenerList<Observer<? super ObservableValueEvent<E>>> theListeners;

		boolean isModifying;

		public ObservableConfigValue(ObservableValue<? extends ObservableConfig> parent, Runnable ceCreate, Observable<?> until,
			TypeToken<E> type, ObservableConfigFormat<E> format, boolean listen) {
			super(parent, ceCreate, until);
			theType = type;
			theFormat = format;

			theListeners = ListenerList.build().allowReentrant().withFastSize(false).build();

			init(until, listen);
		}

		@Override
		public TypeToken<E> getType() {
			return theType;
		}

		@Override
		public E get() {}

		@Override
		public Observable<ObservableValueEvent<E>> noInitChanges() {
			return new Observable<ObservableValueEvent<E>>() {
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
					return ObservableConfigValue.this.lock();
				}

				@Override
				public Transaction tryLock() {
					return ObservableConfigValue.this.tryLock();
				}

				@Override
				public Subscription subscribe(Observer<? super ObservableValueEvent<E>> observer) {
					return theListeners.add(observer, true)::run;
				}
			};
		}

		@Override
		public <V extends E> E set(V value, Object cause) throws IllegalArgumentException, UnsupportedOperationException {}

		@Override
		public <V extends E> String isAcceptable(V value) {
			if (!TypeTokens.get().isInstance(theType, value))
				return StdMsg.ILLEGAL_ELEMENT;
			return null;
		}

		@Override
		public ObservableValue<String> isEnabled() {
			return SettableValue.ALWAYS_ENABLED;
		}

		@Override
		protected void initConfig(ObservableConfig parent, Object cause, boolean listen) {
			// TODO Auto-generated method stub

		}

		@Override
		protected void onChange(ObservableConfigEvent parentChange) {
			// TODO Auto-generated method stub

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
		boolean isModifying;

		public ObservableConfigBackedCollection(ObservableValue<? extends ObservableConfig> collectionElement, Runnable ceCreate,
			TypeToken<E> type, ObservableConfigFormat<E> format, String childName, Observable<?> until, boolean listen) {
			super(collectionElement, ceCreate, until);
			theType = type;
			theFormat = format;
			theChildName = childName;

			theElements = new BetterTreeMap<>(false, ElementId::compareTo);
			theListeners = ListenerList.build().allowReentrant().withFastSize(false).build();

			init(until, listen);

			theCollection = createCollection();
		}

		@Override
		protected void initConfig(ObservableConfig collectionElement, Object cause, boolean listen) {
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
					ConfigElement cve = createElement(child, null);
					theElements.put(child.getParentChildRef(), cve);
					fire(new ObservableCollectionEvent<>(cve.getElementId(), theType, theElements.size() - 1, CollectionChangeType.add,
						null, cve.get(), cause));
				}
				if (listen)
					collectionElement.watch(theChildName).takeUntil(getUntil()).act(this::onChange);
			}
		}

		@Override
		protected void onChange(ObservableConfigEvent collectionChange) {
			if (collectionChange.relativePath.isEmpty() || collectionChange.eventTarget != getParent(false, null))
				return; // Doesn't affect us
			boolean elementChange = collectionChange.relativePath.size() == 1;
			ObservableConfig config = collectionChange.relativePath.get(0);
			if (elementChange && collectionChange.changeType == CollectionChangeType.add) {
				CollectionElement<ElementId> el = theElements.keySet().search(config.getParentChildRef(), SortedSearchFilter.PreferLess);
				ConfigElement newEl;
				if (theNewElement != null)
					newEl = theNewElement;
				else
					newEl = createElement(config, null);
				if (thePreAddAction != null) {
					thePreAddAction.accept(newEl);
					thePreAddAction = null;
				}
				ElementId newElId;
				if (el == null)// Must be empty
					newElId = theElements.putEntry(config.getParentChildRef(), newEl, false).getElementId();
				else if (el.get().compareTo(config.getParentChildRef()) < 0)
					newElId = theElements.putEntry(config.getParentChildRef(), newEl, el.getElementId(), null, true).getElementId();
				else
					newElId = theElements.putEntry(config.getParentChildRef(), newEl, null, el.getElementId(), false).getElementId();
				newEl.theElement = newElId;
				incrementStamp(true);
				fire(new ObservableCollectionEvent<>(newElId, theType, theElements.keySet().getElementsBefore(newElId),
					CollectionChangeType.add, null, newEl.get(), collectionChange));
			} else if (!isModifying) {
				CollectionElement<ConfigElement> el = theElements.getEntry(config.getParentChildRef());
				if (el == null) // Must be a different child
					return;
				if (collectionChange.relativePath.size() == 1 && collectionChange.changeType == CollectionChangeType.remove) {
					incrementStamp(true);
					theElements.mutableEntry(el.getElementId()).remove();
					el.get().dispose();
					fire(new ObservableCollectionEvent<>(el.getElementId(), theType,
						theElements.keySet().getElementsBefore(el.getElementId()), CollectionChangeType.remove, el.get().get(),
						el.get().get(), collectionChange));
				} else {
					try {
						E newValue = theFormat.parse(ObservableValue.of(el.get().getConfig()),
							() -> collectionChange.eventTarget.addChild(theChildName), el.get().get(), collectionChange.asFromChild(),
							getUntil());
						E oldValue = el.get().get();
						incrementStamp(newValue != oldValue);
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
						theNewElement = createElement(cfg, new ValueHolder<>(value.apply(cfg)));
					});
					cve[0] = theNewElement;
					theNewElement = null;
				}
			});
			return cve[0];
		}

		protected abstract OCBCCollection createCollection();

		protected abstract ConfigElement createElement(ObservableConfig config, ValueHolder<E> value);

		protected abstract class ConfigElement implements MutableCollectionElement<E> {
			private final ObservableConfig theConfig;
			private final SimpleObservable<Void> theElementObservable;
			private ElementId theElement;
			private E theValue;
			private CollectionElement<E> immutable;

			public ConfigElement(ObservableConfig config, ValueHolder<E> value) {
				this.theConfig = config;
				theElementObservable = new SimpleObservable<>(null, false, null, b -> b.unsafe());

				E val;
				if (value != null && value.isPresent()) {
					val = value.get();
				} else {
					try {
						val = theFormat.parse(ObservableValue.of(this.theConfig), () -> this.theConfig.getParent().addChild(theChildName),
							null, null, Observable.or(getUntil(), theElementObservable));
					} catch (ParseException e) {
						System.err.println("Could not parse instance for " + this.theConfig);
						e.printStackTrace();
						val = null;
					}
				}
				theValue = val;
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
				try (Transaction t = lock(true, false, null)) {
					if (!theConfig.getParentChildRef().isPresent())
						throw new IllegalArgumentException(StdMsg.ELEMENT_REMOVED);
					isModifying = true;
					_set(value);
					theFormat.format(value, get(), theConfig);
				} finally {
					isModifying = false;
				}
			}

			protected void removeOp() throws UnsupportedOperationException {
				try (Transaction t = lock(true, null)) {
					isModifying = true;
					theConfig.remove();
				} finally {
					isModifying = false;
				}
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
		}

		protected abstract class OCBCCollection implements ObservableCollection<E> {
			@Override
			public long getStamp(boolean structuralOnly) {
				return ObservableConfigBackedCollection.this.getStamp(structuralOnly);
			}

			@Override
			public boolean isLockSupported() {
				return true;
			}

			@Override
			public Transaction lock(boolean write, boolean structural, Object cause) {
				return ObservableConfigBackedCollection.this.lock(write, structural, cause);
			}

			@Override
			public Transaction tryLock(boolean write, boolean structural, Object cause) {
				return ObservableConfigBackedCollection.this.tryLock(write, structural, cause);
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
			public CollectionElement<E> getElementBySource(ElementId sourceEl) {
				CollectionElement<ConfigElement> el = theElements.values().getElementBySource(sourceEl);
				if (el != null)
					return el.get().immutable();
				return MutableCollectionElement.immutable(theElements.get(sourceEl));
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
		}
	}

	static class ObservableConfigValues<E> extends ObservableCollectionWrapper<E> {
		private final Backing<E> theBacking;

		ObservableConfigValues(ObservableValue<? extends ObservableConfig> collectionElement, Runnable ceCreate, TypeToken<E> type,
			ObservableConfigFormat<E> format, String childName, ConfigEntityFieldParser fieldParser, Observable<?> until, boolean listen) {
			theBacking = new Backing<>(collectionElement, ceCreate, type, format, childName, until, listen);
		}

		protected void onChange(ObservableConfigEvent collectionChange) {
			theBacking.onChange(collectionChange);
		}

		static class Backing<E> extends ObservableConfigBackedCollection<E> {
			Backing(ObservableValue<? extends ObservableConfig> collectionElement, Runnable ceCreate, TypeToken<E> type,
				ObservableConfigFormat<E> format, String childName, Observable<?> until, boolean listen) {
				super(collectionElement, ceCreate, type, format, childName, until, listen);
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
			protected ConfigElement createElement(ObservableConfig config, ValueHolder<E> value) {
				return new ConfigElement2(config, value);
			}

			protected class ConfigElement2 extends ConfigElement {
				ConfigElement2(ObservableConfig config, ValueHolder<E> value) {
					super(config, value);
				}

				@Override
				public String isEnabled() {
					return null;
				}

				@Override
				public String isAcceptable(E value) {
					return getCollection().canAdd(value);
				}

				@Override
				public void set(E value) throws UnsupportedOperationException, IllegalArgumentException {
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
		private final ConfigEntityFieldParser theFieldParser;

		ObservableConfigEntityValues(ObservableValue<? extends ObservableConfig> collectionElement, Runnable ceCreate,
			EntityConfigFormat<E> format, String childName, ConfigEntityFieldParser fieldParser, Observable<?> until, boolean listen) {
			super(collectionElement, ceCreate, format.getEntityType().getType(), format, childName, until, listen);

			theFieldParser = fieldParser;
		}

		@Override
		protected EntityConfigFormat<E> getFormat() {
			return (EntityConfigFormat<E>) super.getFormat();
		}

		@Override
		public ConfiguredValueType<E> getType() {
			return getFormat().getEntityType();
		}

		@Override
		public ObservableCollection<? extends E> getValues() {
			return getCollection();
		}

		@Override
		public ValueCreator<E> create(ElementId after, ElementId before, boolean first) {
			return new SimpleValueCreator<E>(getType()) {
				@Override
				public <F> ValueCreator<E> with(ConfiguredValueField<? super E, F> field, F value) throws IllegalArgumentException {
					super.with(field, value);
					theFieldParser.getConfigFormat(field); // Throws an exception if not supported
					getFieldValues().put(field.getIndex(), value);
					return this;
				}

				@Override
				public CollectionElement<E> create(Consumer<? super E> preAddAction) {
					return add(cfg -> {
						try {
							return getFormat().createInstance(cfg, getFieldValues(), getUntil());
						} catch (ParseException e) {
							throw new IllegalStateException("Could not create instance", e);
						}
					}, after, before, first, element -> {
						if (preAddAction != null)
							preAddAction.accept(element.get());
					});
				}
			};
		}

		@Override
		protected OCBCCollection createCollection() {
			return new ConfigValueCollection();
		}

		@Override
		protected ConfigElement createElement(ObservableConfig config, ValueHolder<E> value) {
			return new ConfigValueElement(config, value);
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
			ConfigValueElement(ObservableConfig config, ValueHolder<E> value) {
				super(config, value);
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
