package org.observe.config;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import org.observe.Observable;
import org.observe.Observer;
import org.observe.Subscription;
import org.observe.collect.CollectionChangeType;
import org.observe.collect.CollectionElementMove;
import org.qommons.Causable;
import org.qommons.Identifiable;
import org.qommons.Lockable.CoreId;
import org.qommons.ThreadConstraint;
import org.qommons.Transaction;
import org.qommons.collect.BetterCollections;
import org.qommons.collect.BetterList;
import org.qommons.collect.CollectionLockingStrategy;
import org.qommons.collect.ElementId;
import org.qommons.collect.ListenerList;
import org.qommons.collect.StampedLockingStrategy;
import org.qommons.tree.BetterTreeList;

/** Default, mutable implementation of {@link ObservableConfig} */
public class DefaultObservableConfig extends AbstractObservableConfig {
	private ElementId theParentContentRef;
	private final CollectionLockingStrategy theLocking;
	private List<Cause> theCauses;
	private String theName;
	private String theValue;
	private boolean mayBeTrivial;
	private final BetterList<ObservableConfig> theContent;
	private final ListenerList<InternalObservableConfigListener> theListeners;
	private long theModCount;

	/**
	 * Root constructor. This is protected because this class should be instantiated from {@link #createRoot(String, String, Function)} or
	 * {@link #createRoot(String, ThreadConstraint)}. If this class is inherited, {@link #initialize(DefaultObservableConfig, ElementId)
	 * initialize(null, null)} must be called on this after {@link #setValue(String)}.
	 *
	 * @param name The name for the config
	 * @param locking Generates a locking strategy for the config
	 */
	protected DefaultObservableConfig(String name, Function<Object, CollectionLockingStrategy> locking) {
		if (name.length() == 0)
			throw new IllegalArgumentException("Name must not be empty");
		theLocking = locking.apply(this);
		theName = name;
		theContent = BetterTreeList.<ObservableConfig> build().withLocking(theLocking).build();
		theListeners = ListenerList.build().build();
	}

	/**
	 * Child constructor. This is protected because it should only be called from {@link #createChild(String)} by this class or an inherited
	 * class.
	 *
	 * @param parent The parent config for this config
	 * @param name The name of this config
	 */
	protected DefaultObservableConfig(DefaultObservableConfig parent, String name) {
		if (name.length() == 0)
			throw new IllegalArgumentException("Name must not be empty");
		theLocking = parent.theLocking;
		theName = name;
		theContent = BetterTreeList.<ObservableConfig> build().withLocking(theLocking).build();
		theListeners = ListenerList.build().build();
	}

	/**
	 * @param parent The parent config
	 * @param parentContentRef The id of this child's element in its parent
	 */
	protected void initialize(DefaultObservableConfig parent, ElementId parentContentRef) {
		super.initialize(parent);
		theParentContentRef = parentContentRef;
		theCauses = parent == null ? new ArrayList<>() : parent.theCauses;
	}

	@Override
	public String getName() {
		return theName;
	}

	@Override
	public boolean mayBeTrivial() {
		return mayBeTrivial;
	}

	@Override
	public DefaultObservableConfig setTrivial(boolean mayBeTrivial) {
		this.mayBeTrivial = mayBeTrivial;
		return this;
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
	public ThreadConstraint getThreadConstraint() {
		return theLocking.getThreadConstraint();
	}

	@Override
	public boolean isEventing() {
		// A child cannot change as a result of a parent event
		return theListeners.isFiring()//
			|| (theParentContentRef != null && getParent().isEventing());
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

	@Override
	public Collection<Cause> getCurrentCauses() {
		return Collections.unmodifiableList(theCauses);
	}

	@Override
	public CoreId getCoreId() {
		return theContent.getCoreId();
	}

	private Transaction withCause(Transaction t, Object cause) {
		if (getParent() != null)
			return getParent().withCause(t, cause);
		if (t == null || theCauses == null) // root causable can be null during initialization
			return t;
		if (theCauses.isEmpty()) {
			Causable cause2;
			Transaction causeT;
			if (cause instanceof Causable) {
				cause2 = (Causable) cause;
				causeT = Transaction.NONE;
			} else {
				cause2 = Causable.simpleCause(cause);
				causeT = Causable.use(cause2);
			}
			theCauses.add(cause2);
			return Transaction.and(t, causeT, () -> theCauses.clear());
		} else if (cause instanceof Cause) {
			theCauses.add((Cause) cause);
			return Transaction.and(t, () -> theCauses.remove(cause));
		} else if (cause != null) {
			Causable realCause = Causable.simpleCause(cause);
			theCauses.add(realCause);
			Transaction causeT = realCause.use();
			return Transaction.and(t, () -> {
				theCauses.remove(cause);
				causeT.close();
			});
		} else
			return t;
	}

	private final Object getCurrentCause() {
		if (theCauses == null)
			return getParent().getCurrentCause();
		else if (theCauses.isEmpty())
			return null;
		else if (theCauses.size() == 1)
			return theCauses.get(0);
		else
			return Causable.simpleDelegate(theCauses.toArray());
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
			fire(CollectionChangeType.set, null, BetterList.empty(), oldName, theValue);
		}
		return this;
	}

	@Override
	public ObservableConfig setValue(String value) {
		try (Transaction t = lock(true, null)) {
			String oldValue = theValue;
			theValue = value;
			fire(CollectionChangeType.set, null, //
				BetterList.empty(), theName, oldValue);
		}
		return this;
	}

	@Override
	protected AbstractObservableConfig createChild(String name) {
		return new DefaultObservableConfig(this, name);
	}

	@Override
	protected void addChild(AbstractObservableConfig child, ObservableConfig after, ObservableConfig before, boolean first,
		CollectionElementMove move) {
		ElementId el = theContent.addElement(child, //
			after == null ? null : Objects.requireNonNull(after.getParentChildRef()),
				before == null ? null : Objects.requireNonNull(before.getParentChildRef()), //
					first).getElementId();
		((DefaultObservableConfig) child).initialize(this, el);
		fire(CollectionChangeType.add, move, BetterList.of(child), child.getName(), null);
	}

	@Override
	protected void doRemove(CollectionElementMove move) {
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

	static DefaultObservableConfig createRoot(String name, ThreadConstraint threadConstraint) {
		return createRoot(name, null, v -> new StampedLockingStrategy(v, threadConstraint));
	}

	static DefaultObservableConfig createRoot(String name, String value, Function<Object, CollectionLockingStrategy> locking) {
		DefaultObservableConfig config = new DefaultObservableConfig(name, locking);
		config.setValue(value);
		config.initialize(null, null);
		return config;
	}

	private void fire(CollectionChangeType eventType, CollectionElementMove move, BetterList<ObservableConfig> relativePath, String oldName,
		String oldValue) {
		theModCount++;
		if (!theListeners.isEmpty() && !theListeners.isFiring()) {
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
		public ThreadConstraint getThreadConstraint() {
			return theConfig.getThreadConstraint();
		}

		@Override
		public boolean isEventing() {
			return theConfig.isEventing();
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

		@Override
		public CoreId getCoreId() {
			return theConfig.getCoreId();
		}
	}
}
