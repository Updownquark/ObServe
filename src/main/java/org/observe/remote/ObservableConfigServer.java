package org.observe.remote;

import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.SignatureException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;

import org.observe.remote.ObservableServiceChange.ServiceChangeType;
import org.observe.remote.ObservableServiceClient.ClientId;
import org.observe.remote.SerializedObservableServerChangeSet.Change;
import org.qommons.ArrayUtils;
import org.qommons.ThreadConstraint;
import org.qommons.Transaction;
import org.qommons.collect.BetterList;
import org.qommons.collect.BetterSortedList.SortedSearchFilter;
import org.qommons.collect.BetterSortedSet;
import org.qommons.collect.CollectionElement;
import org.qommons.tree.BetterTreeSet;

public class ObservableConfigServer {
	// com.sun.net.httpserver.HttpServer server;

	public static class ChangeId implements Comparable<ChangeId> {
		public final Instant timeStamp;
		public final int changeId;

		public ChangeId(Instant timeStamp, int changeId) {
			this.timeStamp = timeStamp;
			this.changeId = changeId;
		}

		@Override
		public int compareTo(ChangeId o) {
			int comp = timeStamp.compareTo(o.timeStamp);
			if (comp == 0)
				comp = Integer.compare(changeId, o.changeId);
			return comp;
		}

		public int compareTo(ObservableServiceChange change) {
			int comp = timeStamp.compareTo(change.getTimeStamp());
			if (comp == 0)
				comp = Integer.compare(changeId, change.getChangeId());
			return comp;
		}
	}

	private final LocalObservableClient theLocalClient;
	private ServiceObservableConfig theConfig;
	private final SortedMap<ObservableServiceClient.ClientId, ObservableServiceClient> theClients;
	private final SortedMap<ObservableServiceClient.ClientId, BetterSortedSet<ObservableServiceChange>> theRoleChanges;

	public ObservableConfigServer(LocalObservableClient localClient, ServiceObservableConfig config) {
		theLocalClient = localClient;
		theConfig = config;

		theClients = new TreeMap<>();
		theRoleChanges = new TreeMap<>();
		theClients.put(localClient.getId(), localClient);
	}

	public ServiceObservableConfig getConfig() {
		return theConfig;
	}

	public void grantRole(ObservableServiceRole role, ObservableServiceClient grantee) {
		if (grantee.isGranted(role))
			return;
		if (!theLocalClient.isGranted(role))
			throw new UnsupportedOperationException(
				theConfig.getRootData().getLocalClient() + " is not a member of " + role + " and cannot grant it to anyone else");
		grantee.grant(role);
		NavigableSet<ObservableServiceChange> changes = theRoleChanges.computeIfAbsent(theLocalClient.getId(),
			__ -> BetterTreeSet.buildTreeSet(ObservableServiceChange::compareTo).build());
		changes.add(ObservableServiceChange.createRoleGranted(role, grantee, theLocalClient, Instant.now(),
			theConfig.getRootData().getNextChangeId(), data -> {
				try {
					return theLocalClient.sign(data);
				} catch (SignatureException e) {
					throw new IllegalStateException("Could not sign", e);
				}
			}));
	}

	public void inheritRole(ObservableServiceRole inherited, ObservableServiceRole heir) {
		if (heir.inherits(inherited))
			return;
		LocalObservableClient localClient = theConfig.getRootData().getLocalClient();
		if (!localClient.isGranted(inherited))
			throw new UnsupportedOperationException(theConfig.getRootData().getLocalClient() + " is not a member of " + inherited
				+ " and cannot cause other roles to inherit it");
		heir.addInheritance(inherited);
		NavigableSet<ObservableServiceChange> changes = theRoleChanges.computeIfAbsent(localClient.getId(),
			__ -> BetterTreeSet.buildTreeSet(ObservableServiceChange::compareTo).build());
		changes.add(ObservableServiceChange.createRoleInherited(inherited, heir, localClient, Instant.now(),
			theConfig.getRootData().getNextChangeId(), data -> {
				try {
					return localClient.sign(data);
				} catch (SignatureException e) {
					throw new IllegalStateException("Could not sign", e);
				}
			}));
	}

	public Map<ObservableServiceClient.ClientId, ChangeId> getKnownChanges() {
		Map<ObservableServiceClient.ClientId, ChangeId> knownChanges = theConfig == null ? new HashMap<>()
			: theConfig.getRootData().getKnownChanges();
		for (Map.Entry<ObservableServiceClient.ClientId, BetterSortedSet<ObservableServiceChange>> roleChange : theRoleChanges.entrySet()) {
			ObservableServiceChange last = roleChange.getValue().peekLast();
			if (last != null)
				knownChanges.put(roleChange.getKey(), new ChangeId(last.getTimeStamp(), last.getChangeId()));
		}
		return knownChanges;
	}

	public NavigableSet<ObservableServiceChange> pollChanges(Map<ObservableServiceClient.ClientId, ChangeId> knownChanges) {
		try (Transaction t = theConfig == null ? Transaction.NONE : theConfig.lock(false, null)) {
			NavigableSet<ObservableServiceChange> changes = theConfig == null ? new TreeSet<>()
				: theConfig.getRootData().pollChanges(knownChanges);
			for (Map.Entry<ObservableServiceClient.ClientId, BetterSortedSet<ObservableServiceChange>> roleChange : theRoleChanges
				.entrySet()) {
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
	}

	public void applyChanges(List<SerializedObservableServerChangeSet> changes) {
		List<SerializedObservableServerChangeSet.Change> addChanges = new ArrayList<>();
		try (Transaction t = theConfig == null ? Transaction.NONE : theConfig.lock(true, null)) {
			for (SerializedObservableServerChangeSet changeSet : changes) {
				ObservableServiceClient actor = getOrCreateClient(changeSet.getActorId());
				if (actor == null)
					continue;
				for (SerializedObservableServerChangeSet.Change change : changeSet.getChanges()) {
					try {
						if (!change.validateSignature(actor, changeSet.getTimeStamp())) {
							System.err.println("Invalid digital signature for change " + changeSet.getTimeStamp() + " " + change);
							continue;
						}
					} catch (SignatureException e) {
						System.err.println("Error veryifying digital signature for change " + changeSet.getTimeStamp() + " " + change);
						e.printStackTrace();
						continue;
					}
					if (!addChanges.isEmpty()) {
						if (change.getTargetConfigAddress() != null
							&& startsWith(addChanges.get(0).getTargetConfigAddress(), change.getTargetConfigAddress(), 0)) {
							if (change.getConfigChangeType() == ConfigModificationType.Delete
								&& change.getTargetConfigAddress().size() == addChanges.get(0).getTargetConfigAddress().size())
								addChanges.clear();
							else
								addChanges.add(change);
						} else
							applyAddChanges(actor, changeSet.getTimeStamp(), addChanges);
					}
					if (change.getType() == ServiceChangeType.ConfigAdd) {
						if (!addChanges.isEmpty())
							applyAddChanges(actor, changeSet.getTimeStamp(), addChanges);
						addChanges.add(change);
						continue;
					}
					try (Transaction changeT = change.getConfigChangeType() == null ? Transaction.NONE
						: theConfig.lockForMod(actor, changeSet.getTimeStamp(), change.getChangeId(), change.getSignature().copy(), null)) {
						ServiceObservableConfig config = getConfig(change.getTargetConfigAddress());
						if (config == null)
							continue; // Someone else already removed it
						switch (change.getType()) {
						case ConfigAdd:
							throw new IllegalStateException("Should have been captured above");
						case ConfigRename:
							config.setName(change.getTargetValue());
							break;
						case ConfigModify:
							config.setValue(change.getTargetValue());
							break;
						case ConfigDelete:
							config.remove();
							break;
						case RoleAllow:
							ObservableServiceRole role = getOrCreateRole(change.getTargetRoleOwner(), change.getTargetRoleId(),
								change.getTargetRoleName());
							if (role == null)
								continue;
							config.allow(change.getConfigChangeType(), role);
							break;
						case RoleGrant:
							role = getOrCreateRole(change.getTargetRoleOwner(), change.getTargetRoleId(), change.getTargetRoleName());
							if (role == null)
								continue;
							if (!actor.isGranted(role)) {
								System.err.println(
									"ERROR: Role " + role + " reported granted to a client by " + actor + " who is not granted the role");
								continue;
							}
							ObservableServiceClient grantee = getOrCreateClient(change.getGrantedClient());
							if (grantee == null)
								continue;
							grantee.grant(role);
							theRoleChanges
							.computeIfAbsent(actor.getId(),
								__ -> BetterTreeSet.buildTreeSet(ObservableServiceChange::compareTo).build())//
							.add(ObservableServiceChange.createRoleGranted(role, grantee, actor, changeSet.getTimeStamp(),
								change.getChangeId(), data -> change.getSignature().copy()));
							break;
						case RoleInherit:
							ObservableServiceRole inherited = getOrCreateRole(change.getInheritedRoleOwner(), change.getInheritedRoleId(),
								change.getInheritedRoleName());
							if (inherited == null)
								continue;
							if (!actor.isGranted(inherited)) {
								System.err.println("ERROR: Role " + inherited + " reported inherited to a client by " + actor
									+ " who is not granted the role");
								continue;
							}
							role = getOrCreateRole(change.getTargetRoleOwner(), change.getTargetRoleId(), change.getTargetRoleName());
							if (role == null)
								continue;
							role.addInheritance(inherited);
							theRoleChanges
							.computeIfAbsent(actor.getId(),
								__ -> BetterTreeSet.buildTreeSet(ObservableServiceChange::compareTo).build())//
							.add(ObservableServiceChange.createRoleInherited(inherited, role, actor, changeSet.getTimeStamp(),
								change.getChangeId(), data -> change.getSignature().copy()));
							break;
						case ClientResign:
							int todo; // TODO
							break;
						}
					}
				}
				if (!addChanges.isEmpty())
					applyAddChanges(actor, changeSet.getTimeStamp(), addChanges);
			}
		}
	}

	private boolean startsWith(BetterList<ServerConfigElement> shorter, BetterList<ServerConfigElement> longer, int start) {
		if (longer == null || longer.size() < shorter.size())
			return false;
		Iterator<ServerConfigElement> shortIter = shorter.listIterator(start);
		Iterator<ServerConfigElement> longIter = longer.listIterator(start);
		while (shortIter.hasNext()) {
			if (!shortIter.next().equals(longIter.next()))
				return false;
		}
		return true;
	}

	private void applyAddChanges(ObservableServiceClient actor, Instant timeStamp, List<Change> addChanges) {
		BetterList<ServerConfigElement> address = addChanges.get(0).getTargetConfigAddress();
		if (address.isEmpty() && theConfig == null) { // Create root
			theConfig = ServiceObservableConfig.createRoot(theLocalClient, actor, timeStamp, addChanges.get(0).getChangeId(),
				addChanges.get(0).getSignature().copy(), addChanges.get(0).getTargetValue(), ThreadConstraint.ANY);
			Transaction[] ts = new Transaction[addChanges.size() - 1];
			for (int i = 0; i < ts.length; i++) {
				Change change = addChanges.get(i + 1);
				ts[i] = theConfig.lockForMod(actor, timeStamp, change.getChangeId(), change.getSignature().copy(), change);
			}
			try {
				applyMods(theConfig, address, addChanges.subList(1, addChanges.size()));
			} finally {
				for (int i = ts.length - 1; i >= 0; i--)
					ts[i].close();
				addChanges.clear();
			}
			return;
		}
		List<ServerConfigElement> parentAddress = address.subList(0, address.size() - 1);
		ServiceObservableConfig parent = getConfig(parentAddress);
		if (parent == null) { // Already removed
			addChanges.clear();
			return;
		}
		ServerConfigElement targetAddr = address.get(address.size() - 1);
		if (!targetAddr.owner.equals(actor.getId())) {
			System.err.println("Client " + actor + " is trying to create a config owned by " + targetAddr.owner);
			return;
		}
		Transaction[] ts = new Transaction[addChanges.size()];
		for (int i = 0; i < ts.length; i++) {
			Change change = addChanges.get(i);
			ts[i] = theConfig.lockForMod(actor, timeStamp, change.getChangeId(), change.getSignature().copy(), change);
		}
		try {
			parent.addChild(address.getLast(), addChanges.get(0).getTargetValue(),
				config -> applyMods(config, address, addChanges.subList(1, addChanges.size())));
		} finally {
			for (int i = ts.length - 1; i >= 0; i--)
				ts[i].close();
			addChanges.clear();
		}
	}

	private void applyMods(ServiceObservableConfig config, BetterList<ServerConfigElement> address, List<Change> mods) {
		ServiceObservableConfig addParent = null;
		BetterList<ServerConfigElement> addAddress = null;
		String addName = null;
		List<Change> addChanges = null;
		for (Change mod : mods) {
			if (addAddress != null) {
				if (mod.getTargetConfigAddress() != null && startsWith(addAddress, mod.getTargetConfigAddress(), address.size())) {
					if (mod.getConfigChangeType() == ConfigModificationType.Delete
						&& mod.getTargetConfigAddress().size() == addAddress.size())
						addChanges.clear();
					else
						addChanges.add(mod);
				} else {
					BetterList<ServerConfigElement> fAddress = addAddress;
					List<Change> fChanges = addChanges;
					addParent.addChild(addAddress.getLast(), addName, child -> applyMods(child, fAddress, fChanges));
					addAddress = null;
					addChanges.clear();
				}
			}
			if (mod.getType() == ServiceChangeType.ConfigAdd) {
				addParent = getConfig(config, mod.getTargetConfigAddress().subList(0, mod.getTargetConfigAddress().size() - 1),
					address.size());
				if (addParent == null) {
					System.out.println("Could not add config");
					continue;
				} else if (!mod.getTargetConfigAddress().getLast().owner.equals(address.getLast().owner)) {
					System.out
					.println("Client " + address.getLast().owner + " is tring to create a config owned by " + address.getLast().owner);
					continue;
				}

				if (addChanges == null)
					addChanges = new ArrayList<>();
				addAddress = mod.getTargetConfigAddress();
				addName = mod.getTargetValue();
			} else {
				ServiceObservableConfig target = getConfig(config, mod.getTargetConfigAddress(), address.size());
				if (target == null)
					continue;
				switch (mod.getType()) {
				case ConfigRename:
					target.setName(mod.getTargetValue());
					break;
				case ConfigModify:
					target.setValue(mod.getTargetValue());
					break;
				case RoleAllow:
					ObservableServiceRole role = getOrCreateRole(mod.getTargetRoleOwner(), mod.getInheritedRoleId(),
						mod.getTargetRoleName());
					if (role == null)
						continue;
					target.allow(mod.getConfigChangeType(), role);
					break;
				default:
					System.err.println("Could not interpret event of type " + mod.getType() + " for new child");
					break;
				}
			}
		}
	}

	private ObservableServiceClient getOrCreateClient(ClientId clientId) {
		return theClients.computeIfAbsent(clientId, __ -> {
			X509EncodedKeySpec encodedKey = new X509EncodedKeySpec(clientId.publicKey.copy());
			try {
				KeyFactory keyFactory = KeyFactory.getInstance(clientId.keyAlgorithm);
				PublicKey publicKey = keyFactory.generatePublic(encodedKey);
				return new ObservableServiceClient(publicKey, clientId.signatureAlgorithm);
			} catch (NoSuchAlgorithmException | InvalidKeySpecException | InvalidKeyException e) {
				System.err.println("Invalid client specification");
				e.printStackTrace();
				return null;
			}
		});
	}

	private ServiceObservableConfig getConfig(List<ServerConfigElement> address) {
		return getConfig(theConfig, address, 0);
	}

	ServiceObservableConfig getConfig(ServiceObservableConfig parent, List<ServerConfigElement> address, int addressIdx) {
		if (addressIdx == address.size())
			return parent;
		ServerConfigElement addr = address.get(addressIdx);
		int index = ArrayUtils.binarySearch((List<ServiceObservableConfig>) (List<?>) parent.getContent(),
			config -> addr.compareTo(config));
		ServiceObservableConfig config;
		if (index < 0)
			return null;
		config = (ServiceObservableConfig) parent.getContent().get(index);
		if (addressIdx == address.size() - 1)
			return config;
		return getConfig(config, address, addressIdx + 1);
	}

	private ObservableServiceRole getOrCreateRole(ClientId roleOwner, long roleId, String roleName) {
		ObservableServiceClient client = theClients.computeIfAbsent(roleOwner, __ -> {
			if (roleOwner.equals(ObservableServiceClient.ALL.getId()))
				return ObservableServiceClient.ALL;
			X509EncodedKeySpec encodedKey = new X509EncodedKeySpec(roleOwner.publicKey.copy());
			try {
				KeyFactory keyFactory = KeyFactory.getInstance(roleOwner.keyAlgorithm);
				PublicKey publicKey = keyFactory.generatePublic(encodedKey);
				return new ObservableServiceClient(publicKey, roleOwner.signatureAlgorithm);
			} catch (NoSuchAlgorithmException | InvalidKeySpecException | InvalidKeyException e) {
				System.err.println("Invalid client specification");
				e.printStackTrace();
				return null;
			}
		});
		if (client == null)
			return null;
		return client.getOrCreateRole(roleId, roleName);
	}
}
