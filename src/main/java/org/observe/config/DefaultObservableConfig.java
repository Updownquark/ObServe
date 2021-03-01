package org.observe.config;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import org.observe.Observable;
import org.observe.Observer;
import org.observe.Subscription;
import org.observe.collect.CollectionChangeType;
import org.qommons.Causable;
import org.qommons.Identifiable;
import org.qommons.Transaction;
import org.qommons.ValueHolder;
import org.qommons.collect.BetterCollections;
import org.qommons.collect.BetterList;
import org.qommons.collect.CollectionLockingStrategy;
import org.qommons.collect.ElementId;
import org.qommons.collect.ListenerList;
import org.qommons.collect.StampedLockingStrategy;
import org.qommons.tree.BetterTreeList;

/** Default, mutable implementation of {@link ObservableConfig} */
class DefaultObservableConfig extends AbstractObservableConfig {
	private ElementId theParentContentRef;
	private final CollectionLockingStrategy theLocking;
	private ValueHolder<Causable> theRootCausable;
	private String theName;
	private String theValue;
	private final BetterList<ObservableConfig> theContent;
	private final ListenerList<InternalObservableConfigListener> theListeners;
	private long theModCount;

	private DefaultObservableConfig(String name, Function<Object, CollectionLockingStrategy> locking) {
		if (name.length() == 0)
			throw new IllegalArgumentException("Name must not be empty");
		theLocking = locking.apply(this);
		theName = name;
		theContent = BetterTreeList.<ObservableConfig> build().withLocker(theLocking).build();
		theListeners = ListenerList.build().build();
	}

	private void initialize(DefaultObservableConfig parent, ElementId parentContentRef) {
		super.initialize(parent);
		theParentContentRef = parentContentRef;
		theRootCausable = parent == null ? new ValueHolder<>() : parent.theRootCausable;
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
	public ElementId getParentChildRef() {
		return theParentContentRef;
	}

	@Override
	public DefaultObservableConfig getParent() {
		return (DefaultObservableConfig) super.getParent();
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
	public BetterList<ObservableConfig> getContent() {
		return BetterCollections.unmodifiableList(theContent);
	}

	@Override
	public Observable<ObservableConfigEvent> watch(ObservableConfigPath path) {
		return new ObservableConfigChangesObservable(this, path);
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
	protected AbstractObservableConfig createChild(String name) {
		return new DefaultObservableConfig(name, __ -> theLocking);
	}

	@Override
	protected void addChild(AbstractObservableConfig child, ObservableConfig after, ObservableConfig before, boolean first, boolean move) {
		ElementId el = theContent.addElement(child, //
			after == null ? null : Objects.requireNonNull(after.getParentChildRef()),
				before == null ? null : Objects.requireNonNull(before.getParentChildRef()), //
					first).getElementId();
		((DefaultObservableConfig) child).initialize(this, el);
		fire(CollectionChangeType.add, move, BetterList.of(child), child.getName(), null);
	}

	@Override
	protected void doRemove(boolean move) {
		getParent().theContent.mutableElement(theParentContentRef).remove();
		fire(CollectionChangeType.remove, move, BetterList.empty(), theName, theValue);
	}

	@Override
	protected void _postRemove() {
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
			getParent().fire(eventType, move, //
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
