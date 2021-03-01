package org.observe.config;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.WeakHashMap;
import java.util.function.Consumer;
import java.util.function.Function;

import org.observe.Observable;
import org.observe.Observer;
import org.observe.Subscription;
import org.observe.collect.CollectionChangeType;
import org.qommons.Causable;
import org.qommons.Identifiable;
import org.qommons.Lockable;
import org.qommons.Transaction;
import org.qommons.ValueHolder;
import org.qommons.collect.BetterList;
import org.qommons.collect.CollectionLockingStrategy;
import org.qommons.collect.CollectionUtils;
import org.qommons.collect.CollectionUtils.ElementSyncAction;
import org.qommons.collect.CollectionUtils.ElementSyncInput;
import org.qommons.collect.ElementId;
import org.qommons.collect.ListenerList;
import org.qommons.collect.StampedLockingStrategy;
import org.qommons.tree.BetterTreeList;

/** Default, mutable implementation of {@link ObservableConfig} */
class DefaultObservableConfig implements ObservableConfig {
	private DefaultObservableConfig theParent;
	private ElementId theParentContentRef;
	private final CollectionLockingStrategy theLocking;
	private ValueHolder<Causable> theRootCausable;
	private String theName;
	private String theValue;
	private final BetterList<ObservableConfig> theContent;
	private final ListenerList<InternalObservableConfigListener> theListeners;
	private long theModCount;

	private volatile WeakHashMap<ObservableConfigParseSession, WeakReference<Object>> theParsedItems;

	private DefaultObservableConfig(String name, Function<Object, CollectionLockingStrategy> locking) {
		if (name.length() == 0)
			throw new IllegalArgumentException("Name must not be empty");
		theLocking = locking.apply(this);
		theName = name;
		theContent = BetterTreeList.<ObservableConfig> build().withLocker(theLocking).build();
		theListeners = ListenerList.build().build();
	}

	private DefaultObservableConfig initialize(DefaultObservableConfig parent, ElementId parentContentRef) {
		theParent = parent;
		theParentContentRef = parentContentRef;
		theRootCausable = parent == null ? new ValueHolder<>() : parent.theRootCausable;
		return this;
	}

	@Override
	public Object getParsedItem(ObservableConfigParseSession session) {
		WeakHashMap<ObservableConfigParseSession, WeakReference<Object>> parsedItems = theParsedItems;
		if (parsedItems == null)
			return null;
		WeakReference<Object> ref = parsedItems.get(session);
		return ref == null ? null : ref.get();
	}

	@Override
	public ObservableConfig withParsedItem(ObservableConfigParseSession session, Object item) {
		WeakHashMap<ObservableConfigParseSession, WeakReference<Object>> parsedItems = theParsedItems;
		if (parsedItems == null) {
			synchronized (this) {
				parsedItems = theParsedItems;
				if (parsedItems == null) {
					theParsedItems = parsedItems = new WeakHashMap<>(3); // Don't imagine there will ever be much in there
				}
			}
		}
		parsedItems.compute(session, (s, old) -> {
			Object oldItem = old == null ? null : old.get();
			if (oldItem == item)
				return old;
			else
				return new WeakReference<>(item);
		});
		return this;
	}

	@Override
	public String getName() {
		return theName;
	}

	@Override
	public String getValue() {
		return theValue;
	}

	@Override
	public ObservableConfig getParent() {
		if (theParentContentRef != null && !theParentContentRef.isPresent())
			return null;
		else
			return theParent;
	}

	@Override
	public ElementId getParentChildRef() {
		return theParentContentRef;
	}

	@Override
	public boolean isLockSupported() {
		return theContent.isLockSupported();
	}

	@Override
	public Transaction lock(boolean write, Object cause) {
		return withCause(theContent.lock(write, cause), cause);
	}

	@Override
	public Transaction tryLock(boolean write, Object cause) {
		return withCause(theContent.tryLock(write, cause), cause);
	}

	private Transaction withCause(Transaction t, Object cause) {
		if (t == null || theRootCausable == null) // root causable can be null during initialization
			return t;
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
				Transaction causeT = synCause.use();
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

	private final Object getCurrentCause() {
		return theRootCausable == null ? null : theRootCausable.get();
	}

	@Override
	public long getStamp() {
		return theModCount;
	}

	@Override
	public Observable<ObservableConfigEvent> watch(ObservableConfigPath path) {
		return new ObservableConfigChangesObservable(this, path);
	}

	@Override
	public ObservableConfig addChild(ObservableConfig after, ObservableConfig before, boolean first, String name,
		Consumer<ObservableConfig> preAddMod) {
		return addChild(after, before, first, name, preAddMod, false);
	}

	private DefaultObservableConfig addChild(ObservableConfig after, ObservableConfig before, boolean first, String name,
		Consumer<? super DefaultObservableConfig> preAddMod, boolean move) {
		try (Transaction t = lock(true, null)) {
			DefaultObservableConfig child = new DefaultObservableConfig(name, __ -> theLocking);
			child.theParent = this;
			if (preAddMod != null)
				preAddMod.accept(child);
			addChild(child, after, before, first, move);
			return child;
		}
	}

	private void addChild(DefaultObservableConfig child, ObservableConfig after, ObservableConfig before, boolean first, boolean move) {
		ElementId el = theContent.addElement(child, //
			after == null ? null : Objects.requireNonNull(after.getParentChildRef()),
				before == null ? null : Objects.requireNonNull(before.getParentChildRef()), //
					first).getElementId();
		child.initialize(this, el);
		fire(CollectionChangeType.add, move, BetterList.of(child), child.getName(), null);
	}

	@Override
	public ObservableConfig moveChild(ObservableConfig child, ObservableConfig after, ObservableConfig before, boolean first,
		Runnable afterRemove) {
		try (Transaction t = lock(true, null)) {
			if (child.getParent() != this)
				throw new NoSuchElementException("Config is not a child of this config");
			if (!child.getParentChildRef().isPresent())
				throw new NoSuchElementException("Config has already been removed");

			((DefaultObservableConfig) child)._remove(true);
			if (afterRemove != null)
				afterRemove.run();
			return addChild(after, before, first, child.getName(), newChild -> newChild._copyFrom(child, true, true), true);
		}
	}

	@Override
	public ObservableConfig setName(String name) {
		if (name == null)
			throw new NullPointerException("Name must not be null");
		else if (name.length() == 0)
			throw new IllegalArgumentException("Name must not be empty");
		try (Transaction t = lock(true, null)) {
			String oldName = theName;
			theName = name;
			fire(CollectionChangeType.set, false, BetterList.empty(), oldName, theValue);
		}
		return this;
	}

	@Override
	public ObservableConfig setValue(String value) {
		try (Transaction t = lock(true, null)) {
			String oldValue = theValue;
			theValue = value;
			fire(CollectionChangeType.set, false, //
				BetterList.empty(), theName, oldValue);
		}
		return this;
	}

	@Override
	public ObservableConfig copyFrom(ObservableConfig source, boolean removeExtras) {
		try (Transaction t = Lockable.lockAll(//
			Lockable.lockable(source, false, null), //
			Lockable.lockable(this, true, null))) {
			_copyFrom(source, removeExtras, false);
		}
		return this;
	}

	private void _copyFrom(ObservableConfig source, boolean removeExtras, boolean withParsedItems) {
		if (source instanceof DefaultObservableConfig && ((DefaultObservableConfig) source).theParsedItems != null && withParsedItems) {
			for (Map.Entry<ObservableConfigParseSession, WeakReference<Object>> pi : ((DefaultObservableConfig) source).theParsedItems
				.entrySet()) {
				Object value = pi.getValue().get();
				if (value != null)
					withParsedItem(pi.getKey(), value);
			}
		}
		if (!Objects.equals(theValue, source.getValue()))
			setValue(source.getValue());
		List<DefaultObservableConfig> children = new ArrayList<>(theContent.size());
		children.addAll((BetterList<DefaultObservableConfig>) (BetterList<?>) theContent);
		CollectionUtils.synchronize(children, source.getContent(), (o1, o2) -> {
			return o1.getName().equals(o2.getName());
		}).adjust(new CollectionUtils.CollectionSynchronizer<DefaultObservableConfig, ObservableConfig>() {
			@Override
			public boolean getOrder(ElementSyncInput<DefaultObservableConfig, ObservableConfig> element) {
				return false;
			}

			@Override
			public ElementSyncAction leftOnly(ElementSyncInput<DefaultObservableConfig, ObservableConfig> element) {
				if (removeExtras)
					return element.remove();
				else
					return element.preserve();
			}

			@Override
			public ElementSyncAction rightOnly(ElementSyncInput<DefaultObservableConfig, ObservableConfig> element) {
				ObservableConfig before = element.getTargetIndex() == children.size() ? null : children.get(element.getTargetIndex());
				return element.useValue(addChild(null, before, false, element.getRightValue().getName(),
					newChild -> newChild._copyFrom(element.getRightValue(), removeExtras, withParsedItems), false));
			}

			@Override
			public ElementSyncAction common(ElementSyncInput<DefaultObservableConfig, ObservableConfig> element) {
				element.getLeftValue()._copyFrom(element.getRightValue(), removeExtras, withParsedItems);
				return element.preserve();
			}
		}, CollectionUtils.AdjustmentOrder.RightOrder);
	}

	@Override
	public void remove() {
		_remove(false);
	}

	private void _remove(boolean move) {
		try (Transaction t = lock(true, null)) {
			if (!theParentContentRef.isPresent())
				return;
			theParent.theContent.mutableElement(theParentContentRef).remove();
			fire(CollectionChangeType.remove, move, BetterList.empty(), theName, theValue);
		}
		Map<?, ?> parsedItems = theParsedItems;
		if (parsedItems != null && !move)
			parsedItems.clear();
		theParent = null;
		theParentContentRef = null;
	}

	@Override
	public String toString() {
		return ObservableConfig.toString(this);
	}

	static DefaultObservableConfig createRoot(String name) {
		return createRoot(name, null, v -> new StampedLockingStrategy(v));
	}

	static DefaultObservableConfig createRoot(String name, String value, Function<Object, CollectionLockingStrategy> locking) {
		DefaultObservableConfig config = new DefaultObservableConfig(name, locking);
		config.setValue(value);
		config.initialize(null, null);
		return config;
	}

	private void fire(CollectionChangeType eventType, boolean move, BetterList<ObservableConfig> relativePath, String oldName,
		String oldValue) {
		_fire(eventType, move, relativePath, oldName, oldValue, //
			getCurrentCause());
	}

	private void _fire(CollectionChangeType eventType, boolean move, BetterList<ObservableConfig> relativePath, String oldName,
		String oldValue, Object cause) {
		theModCount++;
		if (!theListeners.isEmpty()) {
			ObservableConfigEvent event = new ObservableConfigEvent(eventType, move, this, oldName, oldValue, relativePath,
				getCurrentCause());
			try (Transaction t = event.use()) {
				theListeners.forEach(intL -> {
					if (intL.path == null || intL.path.matches(relativePath)) {
						if (relativePath.isEmpty() && eventType == CollectionChangeType.remove)
							intL.listener.onCompleted(event);
						else
							intL.listener.onNext(event);
					}
				});
			}
		}
		boolean fireWithParent;
		if (theParentContentRef == null)
			fireWithParent = false;
		else if (theParentContentRef.isPresent())
			fireWithParent = true;
		else
			fireWithParent = eventType == CollectionChangeType.remove && relativePath.isEmpty(); // Means this config was just removed
		if (fireWithParent)
			theParent.fire(eventType, move, //
				addToList(this, relativePath), oldName, oldValue);
	}

	private static BetterList<ObservableConfig> addToList(ObservableConfig c, List<ObservableConfig> list) {
		ObservableConfig[] array = new ObservableConfig[list.size() + 1];
		array[0] = c;
		for (int i = 0; i < list.size(); i++)
			array[i + 1] = list.get(i);
		return BetterList.of(array);
	}

	private static class InternalObservableConfigListener {
		final ObservableConfigPath path;
		final Observer<? super ObservableConfigEvent> listener;

		InternalObservableConfigListener(ObservableConfigPath path, Observer<? super ObservableConfigEvent> observer) {
			this.path = path;
			this.listener = observer;
		}

		@Override
		public String toString() {
			return path + ":" + listener;
		}
	}

	private static class ObservableConfigChangesObservable implements Observable<ObservableConfigEvent> {
		private final DefaultObservableConfig theConfig;
		private final ObservableConfigPath thePath;
		private Object theIdentity;

		ObservableConfigChangesObservable(DefaultObservableConfig config, ObservableConfigPath path) {
			theConfig = config;
			thePath = path;
		}

		@Override
		public Object getIdentity() {
			if (theIdentity == null)
				theIdentity = Identifiable.wrap(theConfig, "watch", thePath);
			return theIdentity;
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
