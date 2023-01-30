package org.observe.remote;

import java.security.SignatureException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;

import org.observe.config.AbstractObservableConfig;
import org.observe.config.DefaultObservableConfig;
import org.observe.config.ObservableConfig;
import org.observe.remote.ObservableConfigServer.ChangeId;
import org.observe.remote.ObservableServiceClient.ClientId;
import org.qommons.ArrayUtils;
import org.qommons.Transaction;
import org.qommons.collect.BetterSortedList.SortedSearchFilter;
import org.qommons.collect.BetterSortedSet;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.CollectionLockingStrategy;
import org.qommons.collect.DequeList;
import org.qommons.collect.ElementId;
import org.qommons.collect.MutableCollectionElement.StdMsg;
import org.qommons.tree.BetterTreeSet;

public class ServiceObservableConfig extends DefaultObservableConfig {
	public static class ServerConfigRootData {
		static class LocalModData {
			private Instant timeStamp;

			Instant getTimeStamp() {
				if (timeStamp == null)
					timeStamp = Instant.now();
				return timeStamp;
			}
		}

		static class ChangeData {
			final int changeId;
			final byte[] signature;

			ChangeData(int changeId, byte[] signature) {
				this.changeId = changeId;
				this.signature = signature;
			}
		}

		private final LocalObservableClient theLocalClient;
		private final AtomicInteger theChangeIdGen;
		private ObservableServiceClient theModifier;
		private Instant theModificationTimeStamp;
		private DequeList<ChangeData> theExpectedChangeData;
		private LocalModData theLocalLock;
		ByteAddress theCreateAddress;

		private Set<ObservableServiceRole> theGlobalModifyRoles;
		private Set<ObservableServiceRole> theGlobalRenameRoles;
		private Set<ObservableServiceRole> theGlobalDeleteRoles;
		private Set<ObservableServiceRole> theGlobalAddRoles;

		private final List<ServiceObservableConfig> theLocallyOwnedConfigs;
		private final SortedMap<ClientId, BetterSortedSet<ObservableServiceChange>> theChanges;

		ServerConfigRootData(LocalObservableClient localClient) {
			theLocalClient = localClient;
			theChangeIdGen = new AtomicInteger();
			theLocallyOwnedConfigs = new ArrayList<>();
			theChanges = new TreeMap<>();
		}

		// public List<ObservableServiceChange> poll(Map<ObservableServiceClient,

		public LocalObservableClient getLocalClient() {
			return theLocalClient;
		}

		public int getNextChangeId() {
			return theChangeIdGen.getAndIncrement();
		}

		private Set<ObservableServiceRole> getInternalRoles(ConfigModificationType type) {
			switch (type) {
			case Modify:
				return theGlobalModifyRoles;
			case Rename:
				return theGlobalRenameRoles;
			case Delete:
				return theGlobalDeleteRoles;
			case Add:
				return theGlobalAddRoles;
			default:
				throw new IllegalStateException("Unrecognized modification type: " + type);
			}
		}

		public Set<ObservableServiceRole> getGlobalRoles(ConfigModificationType type) {
			Set<ObservableServiceRole> roles = getInternalRoles(type);
			return roles == null ? Collections.emptySet() : Collections.unmodifiableSet(roles);
		}

		public void allowForAllLocal(ConfigModificationType type, ObservableServiceRole role) {
			switch (type) {
			case Modify:
				if (theGlobalModifyRoles == null)
					theGlobalModifyRoles = new LinkedHashSet<>();
				if (!theGlobalModifyRoles.add(role))
					return;
				break;
			case Rename:
				if (theGlobalRenameRoles == null)
					theGlobalRenameRoles = new LinkedHashSet<>();
				if (!theGlobalRenameRoles.add(role))
					return;
				break;
			case Delete:
				if (theGlobalDeleteRoles == null)
					theGlobalDeleteRoles = new LinkedHashSet<>();
				if (!theGlobalDeleteRoles.add(role))
					return;
				break;
			case Add:
				if (theGlobalAddRoles == null)
					theGlobalAddRoles = new LinkedHashSet<>();
				if (!theGlobalAddRoles.add(role))
					return;
				break;
			}
			// Need to fire events on all locally-owned configs
			NavigableSet<ObservableServiceChange> changes = theChanges.computeIfAbsent(theLocalClient.getId(),
				__ -> BetterTreeSet.buildTreeSet(ObservableServiceChange::compareTo).build());
			Instant timeStamp = theModificationTimeStamp;
			if (timeStamp == null)
				timeStamp = Instant.now();
			for (ServiceObservableConfig config : theLocallyOwnedConfigs) {
				Set<ObservableServiceRole> perms = config.getInternalRoles(type);
				if (perms != null && perms.remove(role)) // Config already allowed that permission
					continue;
				changes.add(ObservableServiceChange.createRoleAllowed(config, role, type, theLocalClient, timeStamp,
					theChangeIdGen.getAndIncrement(), data -> {
						try {
							return theLocalClient.sign(data);
						} catch (SignatureException e) {
							throw new IllegalStateException("Signature failed", e);
						}
					}));
			}
		}

		public Map<ObservableServiceClient.ClientId, ObservableConfigServer.ChangeId> getKnownChanges() {
			Map<ObservableServiceClient.ClientId, ObservableConfigServer.ChangeId> knownChanges = new HashMap<>();
			for (Map.Entry<ObservableServiceClient.ClientId, BetterSortedSet<ObservableServiceChange>> roleChange : theChanges.entrySet()) {
				ObservableServiceChange last = roleChange.getValue().peekLast();
				if (last != null)
					knownChanges.put(roleChange.getKey(), new ChangeId(last.getTimeStamp(), last.getChangeId()));
			}
			return knownChanges;
		}

		public NavigableSet<ObservableServiceChange> pollChanges(
			Map<ObservableServiceClient.ClientId, ObservableConfigServer.ChangeId> knownChanges) {
			NavigableSet<ObservableServiceChange> changes = new TreeSet<>();
			for (Map.Entry<ObservableServiceClient.ClientId, BetterSortedSet<ObservableServiceChange>> roleChange : theChanges.entrySet()) {
				ChangeId lastKnown = knownChanges.get(roleChange.getKey());
				if (lastKnown == null)
					changes.addAll(roleChange.getValue());
				else {
					CollectionElement<ObservableServiceChange> later = roleChange.getValue().search(change -> lastKnown.compareTo(change),
						SortedSearchFilter.Greater);
					while (later != null) {
						changes.add(later.get());
						later = roleChange.getValue().getAdjacentElement(later.getElementId(), true);
					}
				}
			}
			return changes;
		}

		Transaction lockForMod(Transaction lockT) {
			if (theLocalLock != null)
				return lockT; // Already locked
			theLocalLock = new LocalModData();
			return () -> {
				theLocalLock = null;
				lockT.close();
			};
		}

		Transaction lockForExternalMod(Transaction lockT, ObservableServiceClient actor, Instant timeStamp, int changeId,
			byte[] signature) {
			if (theModifier != null) {
				if (theModifier != actor || !theModificationTimeStamp.equals(timeStamp))
					throw new IllegalArgumentException(
						"This config is already being modified by " + theModifier + ", cannot be modified by " + actor);
				return lockT;
			}
			theModifier = actor;
			theModificationTimeStamp = timeStamp;
			theExpectedChangeData.add(new ChangeData(changeId, signature));
			return () -> {
				theModifier = null;
				theModificationTimeStamp = null;
				lockT.close();
			};
		}

		ObservableServiceClient getModificationActor() {
			if (theModifier != null)
				return theModifier;
			else
				return theLocalClient;
		}

		private ObservableServiceChange createConfigChange(ServiceObservableConfig config, ConfigModificationType type, String value) {
			ObservableServiceClient modifier;
			Instant timeStamp;
			int changeId;
			byte[] signature;
			if (theModifier == null) {
				modifier = theLocalClient;
				timeStamp = theLocalLock.getTimeStamp();
				changeId = theChangeIdGen.getAndIncrement();
				signature = null;
			} else {
				modifier = theModifier;
				timeStamp = theModificationTimeStamp;
				ChangeData changeData = theExpectedChangeData.remove();
				changeId = changeData.changeId;
				signature = changeData.signature;
			}
			return ObservableServiceChange.createConfigChange(config, type, value, modifier, timeStamp, changeId, data -> {
				try {
					if (theModifier == null)
						return theLocalClient.sign(data);
					else
						return signature; // Pre-verified elsewhere
				} catch (SignatureException e) {
					throw new IllegalStateException("Signature failed", e);
				}
			});
		}

		private ObservableServiceChange createPermissionChange(ServiceObservableConfig config, ObservableServiceRole role,
			ConfigModificationType type) {
			ObservableServiceClient modifier;
			Instant timeStamp;
			int changeId;
			byte[] signature;
			if (theModifier == null) {
				modifier = theLocalClient;
				timeStamp = theLocalLock.getTimeStamp();
				changeId = theChangeIdGen.getAndIncrement();
				signature = null;
			} else {
				modifier = theModifier;
				timeStamp = theModificationTimeStamp;
				ChangeData changeData = theExpectedChangeData.remove();
				changeId = changeData.changeId;
				signature = changeData.signature;
			}
			return ObservableServiceChange.createRoleAllowed(config, role, type, modifier, timeStamp, changeId, data -> {
				try {
					if (theModifier == null)
						return theLocalClient.sign(data);
					else
						return signature; // Pre-verified elsewhere
				} catch (SignatureException e) {
					throw new IllegalStateException("Signature failed", e);
				}
			});
		}

		ObservableServiceChange added(ServiceObservableConfig config) {
			ObservableServiceChange change = createConfigChange(config, ConfigModificationType.Add, config.getName());
			NavigableSet<ObservableServiceChange> changes = theChanges.computeIfAbsent(getModificationActor().getId(),
				__ -> BetterTreeSet.buildTreeSet(ObservableServiceChange::compareTo).build());
			changes.add(change);
			if (config.getValue() != null) {
				ObservableServiceChange modify = createConfigChange(config, ConfigModificationType.Modify, config.getValue());
				changes.add(modify);
				config.theLastModify = modify;
			}
			if (config.getOwner() == theLocalClient) {
				if (theGlobalAddRoles != null) {
					for (ObservableServiceRole role : theGlobalAddRoles)
						changes.add(createPermissionChange(config, role, ConfigModificationType.Add));
				}
				if (theGlobalRenameRoles != null) {
					for (ObservableServiceRole role : theGlobalRenameRoles)
						changes.add(createPermissionChange(config, role, ConfigModificationType.Rename));
				}
				if (theGlobalModifyRoles != null) {
					for (ObservableServiceRole role : theGlobalModifyRoles)
						changes.add(createPermissionChange(config, role, ConfigModificationType.Modify));
				}
				if (theGlobalDeleteRoles != null) {
					for (ObservableServiceRole role : theGlobalDeleteRoles)
						changes.add(createPermissionChange(config, role, ConfigModificationType.Delete));
				}
			}
			if (config.theAddRoles != null) {
				for (ObservableServiceRole role : config.theAddRoles)
					changes.add(createPermissionChange(config, role, ConfigModificationType.Add));
			}
			if (config.theRenameRoles != null) {
				for (ObservableServiceRole role : config.theRenameRoles)
					changes.add(createPermissionChange(config, role, ConfigModificationType.Rename));
			}
			if (config.theModifyRoles != null) {
				for (ObservableServiceRole role : config.theModifyRoles)
					changes.add(createPermissionChange(config, role, ConfigModificationType.Modify));
			}
			if (config.theDeleteRoles != null) {
				for (ObservableServiceRole role : config.theDeleteRoles)
					changes.add(createPermissionChange(config, role, ConfigModificationType.Delete));
			}
			return change;
		}

		ObservableServiceChange renamed(ServiceObservableConfig config) {
			ObservableServiceChange change = createConfigChange(config, ConfigModificationType.Rename, config.getName());
			theChanges.computeIfAbsent(getModificationActor().getId(),
				__ -> BetterTreeSet.buildTreeSet(ObservableServiceChange::compareTo).build()).add(change);
			return change;
		}

		ObservableServiceChange modified(ServiceObservableConfig config) {
			ObservableServiceChange change = createConfigChange(config, ConfigModificationType.Modify, config.getValue());
			theChanges.computeIfAbsent(getModificationActor().getId(),
				__ -> BetterTreeSet.buildTreeSet(ObservableServiceChange::compareTo).build()).add(change);
			return change;
		}

		void deleted(ServiceObservableConfig config) {
			theChanges
			.computeIfAbsent(getModificationActor().getId(),
				__ -> BetterTreeSet.buildTreeSet(ObservableServiceChange::compareTo).build())//
			.add(createConfigChange(config, ConfigModificationType.Delete, null));
		}

		void purge(ObservableServiceChange change) {
			NavigableSet<ObservableServiceChange> changes = theChanges.get(change.getActor().getId());
			if (changes != null)
				changes.remove(change);
		}
	}

	private final ServerConfigRootData theRootData;
	private final ObservableServiceClient theOwner;
	private ByteAddress theAddress;
	private Set<ObservableServiceRole> theModifyRoles;
	private Set<ObservableServiceRole> theRenameRoles;
	private Set<ObservableServiceRole> theDeleteRoles;
	private Set<ObservableServiceRole> theAddRoles;

	private ObservableServiceChange theCreation;
	private ObservableServiceChange theLastRename;
	private ObservableServiceChange theLastModify;

	public ServiceObservableConfig(ServiceObservableConfig parent, String name) {
		super(parent, name);
		theRootData = parent.theRootData;
		theOwner = theRootData.getModificationActor();
	}

	public ServiceObservableConfig(LocalObservableClient localClient, ObservableServiceClient owner, String name,
		Function<Object, CollectionLockingStrategy> locking) {
		super(name, locking);
		theRootData = new ServerConfigRootData(localClient);
		theOwner = owner;
	}

	public ServerConfigRootData getRootData() {
		return theRootData;
	}

	@Override
	public ServiceObservableConfig getParent() {
		return (ServiceObservableConfig) super.getParent();
	}

	@Override
	protected void initialize(DefaultObservableConfig parent, ElementId parentContentRef) {
		super.initialize(parent, parentContentRef);
		if (parent == null) {
			theAddress = new ByteAddress(new byte[0]);
		} else if (theRootData.theCreateAddress != null) {
			theAddress = theRootData.theCreateAddress;
			theRootData.theCreateAddress = null;
		} else {
			CollectionElement<ObservableConfig> prev = parent.getContent().getAdjacentElement(parentContentRef, false);
			CollectionElement<ObservableConfig> next = parent.getContent().getAdjacentElement(parentContentRef, true);
			theAddress = ByteAddress.between(//
				prev == null ? null : ((ServiceObservableConfig) prev.get()).getAddress(), //
					next == null ? null : ((ServiceObservableConfig) next.get()).getAddress());
			if (theAddress.size() > 255)
				throw new IllegalStateException("This byte address is too large to serialize!");
		}
		theCreation = theRootData.added(this);
	}

	public Transaction lockForMod(ObservableServiceClient actor, Instant timeStamp, int changeId, byte[] signature, Object cause) {
		Transaction lockT = super.lock(true, cause);
		return theRootData.lockForExternalMod(lockT, actor, timeStamp, changeId, signature);
	}

	public Transaction tryLockForMod(ObservableServiceClient actor, Instant timeStamp, int changeId, byte[] signature, Object cause) {
		Transaction lockT = super.tryLock(true, cause);
		if (lockT == null)
			return null;
		return theRootData.lockForExternalMod(lockT, actor, timeStamp, changeId, signature);
	}

	@Override
	public Transaction lock(boolean write, Object cause) {
		Transaction lockT = super.lock(write, cause);
		if (write)
			return theRootData.lockForMod(lockT);
		else
			return lockT;
	}

	@Override
	public Transaction tryLock(boolean write, Object cause) {
		Transaction lockT = super.tryLock(write, cause);
		if (write && lockT != null)
			return theRootData.lockForMod(lockT);
		else
			return lockT;
	}

	public boolean can(ObservableServiceClient client, ConfigModificationType type) {
		if (client == theOwner)
			return true;
		Set<ObservableServiceRole> roles = getInternalRoles(type);
		if (roles != null) {
			if (roles.contains(ObservableServiceRole.ALL))
				return true;
			if (can(roles, client.getAssignedRoles()))
				return true;
		}
		roles = theRootData.getInternalRoles(type);
		if (roles != null) {
			if (roles.contains(ObservableServiceRole.ALL))
				return true;
			if (can(roles, client.getAssignedRoles()))
				return true;
		}
		return false;
	}

	private boolean can(Set<ObservableServiceRole> allowed, Set<ObservableServiceRole> assigned) {
		for (ObservableServiceRole role : assigned) {
			if (allowed.contains(role))
				return true;
			else if (can(allowed, role.getInheritance()))
				return true;
		}
		return false;
	}

	@Override
	public String canAddChild(ObservableConfig after, ObservableConfig before) {
		ObservableServiceClient actor = theRootData.getModificationActor();
		if (!can(actor, ConfigModificationType.Add))
			return StdMsg.UNSUPPORTED_OPERATION + ": " + actor + " does not have permission to add children under this config: "
			+ getPath();
		return super.canAddChild(after, before);
	}

	@Override
	public String canMoveChild(ObservableConfig child, ObservableConfig after, ObservableConfig before) {
		ObservableServiceClient actor = theRootData.getModificationActor();
		if (actor != theRootData.getLocalClient())
			return StdMsg.UNSUPPORTED_OPERATION + ": only one's own configs can be moved: " + child.getPath();
		return super.canMoveChild(child, after, before);
	}

	@Override
	public String canSetValue(String value) {
		ObservableServiceClient actor = theRootData.getModificationActor();
		if (!can(actor, ConfigModificationType.Modify))
			return StdMsg.UNSUPPORTED_OPERATION + ": " + actor + " does not have permission to modify this config's value: " + getPath();
		return super.canSetValue(value);
	}

	@Override
	public String canRemove() {
		ObservableServiceClient actor = theRootData.getModificationActor();
		if (!can(actor, ConfigModificationType.Delete))
			return StdMsg.UNSUPPORTED_OPERATION + ": " + actor + " does not have permission to delete this config: " + getPath();
		return super.canRemove();
	}

	@Override
	public ServiceObservableConfig setName(String name) {
		try (Transaction t = lock(true, null)) {
			ObservableServiceClient actor = theRootData.getModificationActor();
			if (!can(actor, ConfigModificationType.Rename))
				throw new UnsupportedOperationException(
					StdMsg.UNSUPPORTED_OPERATION + ": " + actor + " does not have permission to rename this config: " + getPath());
			super.setName(name);
			if (theCreation != null) {
				ObservableServiceChange change = theRootData.renamed(this);
				if (theLastRename != null)
					theRootData.purge(theLastRename);
				theLastRename = change;
			}
			return this;
		}
	}

	@Override
	public ServiceObservableConfig setValue(String value) {
		try (Transaction t = lock(true, null)) {
			ObservableServiceClient actor = theRootData.getModificationActor();
			if (!can(actor, ConfigModificationType.Modify))
				throw new UnsupportedOperationException(
					StdMsg.UNSUPPORTED_OPERATION + ": " + actor + " does not have permission to modify this config's value: " + getPath());
			super.setValue(value);
			if (theCreation != null) {
				ObservableServiceChange change = theRootData.modified(this);
				if (theLastModify != null)
					theRootData.purge(theLastModify);
				theLastModify = change;
			}
			return this;
		}
	}

	@Override
	public ServiceObservableConfig moveChild(ObservableConfig child, ObservableConfig after, ObservableConfig before, boolean first,
		Runnable afterRemove) {
		try (Transaction t = lock(true, null)) {
			ObservableServiceClient actor = theRootData.getModificationActor();
			if (actor != theRootData.getLocalClient())
				throw new UnsupportedOperationException(
					StdMsg.UNSUPPORTED_OPERATION + ": only one's own configs can be moved: " + child.getPath());
			ServiceObservableConfig newChild = (ServiceObservableConfig) super.moveChild(child, after, before, first, afterRemove);
			ServiceObservableConfig serverChild = (ServiceObservableConfig) child;
			theRootData.deleted(serverChild);
			newChild.theAddRoles = serverChild.theAddRoles;
			newChild.theModifyRoles = serverChild.theModifyRoles;
			newChild.theRenameRoles = serverChild.theRenameRoles;
			newChild.theDeleteRoles = serverChild.theDeleteRoles;
			theRootData.added(newChild);
			return newChild;
		}
	}

	@Override
	public void remove() {
		try (Transaction t = lock(true, null)) {
			ObservableServiceClient actor = theRootData.getModificationActor();
			if (!can(actor, ConfigModificationType.Delete))
				throw new UnsupportedOperationException(
					StdMsg.UNSUPPORTED_OPERATION + ": " + actor + " does not have permission to delete this config: " + getPath());
			super.remove();
			theRootData.deleted(this);
		}
	}

	public ServiceObservableConfig addChild(ServerConfigElement address, String name, Consumer<ServiceObservableConfig> configure) {
		int index = ArrayUtils.binarySearch(getContent(), config -> address.compareTo((ServiceObservableConfig) config));
		if (index >= 0) {
			ServiceObservableConfig config = (ServiceObservableConfig) getContent().get(index);
			if (configure != null)
				configure.accept(config);
			return config;
		} else {
			theRootData.theCreateAddress = address.address;
			try {
				if (getContent().isEmpty())
					return (ServiceObservableConfig) addChild(name, config -> configure.accept((ServiceObservableConfig) config));
				else
					return (ServiceObservableConfig) addChild(null, getContent().get(-index - 1), true, name,
						config -> configure.accept((ServiceObservableConfig) config));
			} finally {
				theRootData.theCreateAddress = null;
			}
		}
	}

	public ObservableServiceClient getOwner() {
		return theOwner;
	}

	public ByteAddress getAddress() {
		return theAddress;
	}

	public List<ServerConfigElement> getAddressPath() {
		List<ServerConfigElement> path = new ArrayList<>();
		ServiceObservableConfig config = this;
		while (true) {
			ServiceObservableConfig parent = config.getParent();
			if (parent != null) {
				path.add(new ServerConfigElement(config.getOwner().getId(), config.getAddress()));
				config = parent;
			} else
				break;
		}
		Collections.reverse(path);
		return path;
	}

	private Set<ObservableServiceRole> getInternalRoles(ConfigModificationType type) {
		switch (type) {
		case Modify:
			return theModifyRoles;
		case Rename:
			return theRenameRoles;
		case Delete:
			return theDeleteRoles;
		case Add:
			return theAddRoles;
		default:
			throw new IllegalStateException("Unrecognized modification type: " + type);
		}
	}

	public Set<ObservableServiceRole> getRoles(ConfigModificationType type) {
		Set<ObservableServiceRole> roles = getInternalRoles(type);
		return roles == null ? Collections.emptySet() : Collections.unmodifiableSet(roles);
	}

	public ServiceObservableConfig allow(ConfigModificationType type, ObservableServiceRole role) {
		if (theOwner == theRootData.getLocalClient()) {
			Set<ObservableServiceRole> globalRoles = theRootData.getInternalRoles(type);
			if (globalRoles != null && globalRoles.contains(role))
				return this; // Already allowed globally
		}
		switch (type) {
		case Modify:
			if (theModifyRoles == null)
				theModifyRoles = new LinkedHashSet<>();
			theModifyRoles.add(role);
			break;
		case Rename:
			if (theRenameRoles == null)
				theRenameRoles = new LinkedHashSet<>();
			theRenameRoles.add(role);
			break;
		case Delete:
			if (theDeleteRoles == null)
				theDeleteRoles = new LinkedHashSet<>();
			theDeleteRoles.add(role);
			break;
		case Add:
			if (theAddRoles == null)
				theAddRoles = new LinkedHashSet<>();
			theAddRoles.add(role);
			break;
		}
		return this;
	}

	@Override
	protected AbstractObservableConfig createChild(String name) {
		try (Transaction t = lock(true, null)) {
			ObservableServiceClient actor = theRootData.getModificationActor();
			if (!can(actor, ConfigModificationType.Add))
				throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION + ": " + actor
					+ " does not have permission to add children under this config: " + getPath());
			return new ServiceObservableConfig(this, name);
		}
	}

	public static ServiceObservableConfig createRoot(LocalObservableClient localClient, ObservableServiceClient owner, String name,
		Function<Object, CollectionLockingStrategy> locking) {
		ServiceObservableConfig root = new ServiceObservableConfig(localClient, owner, name, locking);
		root.initialize(null, null);
		return root;
	}
}
