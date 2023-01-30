package org.observe.remote;

import java.security.SignatureException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.SortedSet;

import org.observe.remote.ObservableServiceChange.ServiceChangeType;
import org.qommons.collect.BetterList;

public class SerializedObservableServerChangeSet {
	public static class Change {
		private final int theChangeId;
		private final ServiceChangeType theType;
		private final BetterList<ServerConfigElement> theTargetConfigAddress;
		private final ConfigModificationType theConfigChangeType;
		private final String theTargetValue;
		private final ObservableServiceClient.ClientId theTargetRoleOwner;
		private final long theTargetRoleId;
		private final String theTargetRoleName;
		private final ObservableServiceClient.ClientId theGrantedClient;
		private final ObservableServiceClient.ClientId theInheritedRoleOwner;
		private final long theInheritedRoleId;
		private final String theInheritedRoleName;
		private final ByteArray theSignature;

		public Change(Instant timeStamp, int changeId, ServiceChangeType type, BetterList<ServerConfigElement> targetConfigAddress,
			ConfigModificationType configChangeType, String targetValue, ObservableServiceClient.ClientId targetRoleOwner,
			long targetRoleId, String targetRoleName, ObservableServiceClient.ClientId grantedClient,
			ObservableServiceClient.ClientId inheritedRoleOwner, long inheritedRoleId, String inheritedRoleName, ByteArray signature) {
			theChangeId = changeId;
			theType = type;
			theTargetConfigAddress = targetConfigAddress;
			theConfigChangeType = configChangeType;
			theTargetValue = targetValue;
			theTargetRoleOwner = targetRoleOwner;
			theTargetRoleId = targetRoleId;
			theTargetRoleName = targetRoleName;
			theGrantedClient = grantedClient;
			theInheritedRoleOwner = inheritedRoleOwner;
			theInheritedRoleId = inheritedRoleId;
			theInheritedRoleName = inheritedRoleName;
			theSignature = signature;
		}

		public Change(ObservableServiceChange change) {
			theChangeId = change.getChangeId();
			theType = change.getType();
			theTargetConfigAddress = change.getTargetConfig() == null ? null : BetterList.of(change.getTargetConfig());
			theConfigChangeType = change.getConfigChangeType();
			theTargetValue = change.getTargetValue();
			if (change.getTargetRole() == null) {
				theTargetRoleOwner = null;
				theTargetRoleId = 0;
				theTargetRoleName = null;
			} else {
				theTargetRoleOwner = change.getTargetRole().getOwner().getId();
				theTargetRoleId = change.getTargetRole().getId();
				theTargetRoleName = change.getTargetRole().getName();
			}
			theGrantedClient = change.getGrantedClient() == null ? null : change.getGrantedClient().getId();
			if (change.getInheritedRole() == null) {
				theInheritedRoleOwner = null;
				theInheritedRoleId = 0;
				theInheritedRoleName = null;
			} else {
				theInheritedRoleOwner = change.getInheritedRole().getOwner().getId();
				theInheritedRoleId = change.getInheritedRole().getId();
				theInheritedRoleName = change.getInheritedRole().getName();
			}
			theSignature = change.getSignature();
		}

		public int getChangeId() {
			return theChangeId;
		}

		public ServiceChangeType getType() {
			return theType;
		}

		public BetterList<ServerConfigElement> getTargetConfigAddress() {
			return theTargetConfigAddress;
		}

		public ConfigModificationType getConfigChangeType() {
			return theConfigChangeType;
		}

		public String getTargetValue() {
			return theTargetValue;
		}

		public ObservableServiceClient.ClientId getTargetRoleOwner() {
			return theTargetRoleOwner;
		}

		public long getTargetRoleId() {
			return theTargetRoleId;
		}

		public String getTargetRoleName() {
			return theTargetRoleName;
		}

		public ObservableServiceClient.ClientId getGrantedClient() {
			return theGrantedClient;
		}

		public ObservableServiceClient.ClientId getInheritedRoleOwner() {
			return theInheritedRoleOwner;
		}

		public long getInheritedRoleId() {
			return theInheritedRoleId;
		}

		public String getInheritedRoleName() {
			return theInheritedRoleName;
		}

		public ByteArray getSignature() {
			return theSignature;
		}

		public boolean validateSignature(ObservableServiceClient client, Instant timeStamp) throws SignatureException {
			byte[] encoded = ObservableServiceChange.encodeForSignature(timeStamp, theChangeId, theType, theTargetConfigAddress,
				theConfigChangeType, theTargetValue, theTargetRoleOwner, theTargetRoleId, theGrantedClient, theInheritedRoleOwner,
				theInheritedRoleId);
			return client.verifySignature(encoded, theSignature.copy());
		}
	}

	private final ObservableServiceClient.ClientId theActorId;
	private final Instant theTimeStamp;
	private final List<Change> theChanges;

	public SerializedObservableServerChangeSet(ObservableServiceClient.ClientId actorId, Instant timeStamp, List<Change> changes) {
		if (actorId == null || timeStamp == null || changes == null)
			throw new NullPointerException();
		theActorId = actorId;
		theTimeStamp = timeStamp;
		theChanges = changes;
	}

	public ObservableServiceClient.ClientId getActorId() {
		return theActorId;
	}

	public Instant getTimeStamp() {
		return theTimeStamp;
	}

	public List<Change> getChanges() {
		return theChanges;
	}

	public static List<SerializedObservableServerChangeSet> serialize(SortedSet<ObservableServiceChange> changes) {
		if (changes.isEmpty())
			return Collections.emptyList();
		List<SerializedObservableServerChangeSet> changeSets = new ArrayList<>();
		ObservableServiceClient.ClientId actor = null;
		Instant timeStamp = null;
		List<Change> setChanges = new ArrayList<>();
		for (ObservableServiceChange change : changes) {
			if (!change.getActor().getId().equals(actor) || !change.getTimeStamp().equals(timeStamp)) {
				if (setChanges.isEmpty()) {//
				} else if (setChanges.size() == 1)
					changeSets.add(new SerializedObservableServerChangeSet(actor, timeStamp, Collections.singletonList(setChanges.get(0))));
				else
					changeSets.add(new SerializedObservableServerChangeSet(actor, timeStamp,
						BetterList.of(setChanges.toArray(new Change[setChanges.size()]))));

				setChanges.clear();
				actor = change.getActor().getId();
				timeStamp = change.getTimeStamp();
			}
			setChanges.add(new Change(change));
		}
		if (setChanges.size() == 1)
			changeSets.add(new SerializedObservableServerChangeSet(actor, timeStamp, Collections.singletonList(setChanges.get(0))));
		else
			changeSets.add(new SerializedObservableServerChangeSet(actor, timeStamp, Collections.unmodifiableList(setChanges)));

		return changeSets;
	}
}
