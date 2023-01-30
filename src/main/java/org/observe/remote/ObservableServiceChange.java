package org.observe.remote;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.time.Instant;
import java.util.List;

public class ObservableServiceChange implements Comparable<ObservableServiceChange> {
	public static enum ServiceChangeType {
		ConfigAdd, //
		ConfigModify, //
		ConfigRename, //
		ConfigDelete, //
		RoleAllow, //
		RoleGrant, //
		RoleInherit//
	}

	private final ObservableServiceClient theActor;
	private final Instant theTimeStamp;
	private final int theChangeId;
	private final ServiceChangeType theType;
	private final ServiceObservableConfig theTargetConfig;
	private final ConfigModificationType theConfigChangeType;
	private final String theTargetValue;
	private final ObservableServiceRole theTargetRole;
	private final ObservableServiceClient theGrantedClient;
	private final ObservableServiceRole theInheritedRole;
	private final ByteArray theSignature;

	private ObservableServiceChange(ObservableServiceClient actor, Instant timeStamp, int changeId, ServiceChangeType type,
		ServiceObservableConfig targetConfig, ConfigModificationType configChangeType, String targetValue, ObservableServiceRole targetRole,
		ObservableServiceClient grantedClient, ObservableServiceRole inheritedRole, ByteArray signature) {
		theActor = actor;
		theTimeStamp = timeStamp;
		theChangeId = changeId;
		theType = type;
		theTargetConfig = targetConfig;
		theConfigChangeType = configChangeType;
		theTargetValue = targetValue;
		theTargetRole = targetRole;
		theGrantedClient = grantedClient;
		theInheritedRole = inheritedRole;
		theSignature = signature;
	}

	public ObservableServiceClient getActor() {
		return theActor;
	}

	public Instant getTimeStamp() {
		return theTimeStamp;
	}

	public int getChangeId() {
		return theChangeId;
	}

	public ServiceChangeType getType() {
		return theType;
	}

	public ServiceObservableConfig getTargetConfig() {
		return theTargetConfig;
	}

	public ConfigModificationType getConfigChangeType() {
		return theConfigChangeType;
	}

	public String getTargetValue() {
		return theTargetValue;
	}

	public ObservableServiceRole getTargetRole() {
		return theTargetRole;
	}

	public ObservableServiceClient getGrantedClient() {
		return theGrantedClient;
	}

	public ObservableServiceRole getInheritedRole() {
		return theInheritedRole;
	}

	public ByteArray getSignature() {
		return theSignature;
	}

	@Override
	public int compareTo(ObservableServiceChange o) {
		int comp = theTimeStamp.compareTo(o.theTimeStamp);
		if (comp == 0)
			comp = theActor.compareTo(o.theActor);
		if (comp == 0)
			comp = Integer.compare(theChangeId, o.theChangeId);
		return comp;
	}

	public static final Charset UTF_8 = Charset.forName("UTF-8");

	public static byte[] encodeForSignature(Instant timeStamp, int changeId, ServiceChangeType type, //
		List<ServerConfigElement> targetConfigAddress, ConfigModificationType configChangeType, String targetValue, //
		ObservableServiceClient.ClientId targetRoleOwner, long targetRoleId, //
		ObservableServiceClient.ClientId grantedClient, //
		ObservableServiceClient.ClientId inheritedRoleOwner, long inheritedRoleId) {
		int serializedSize = 12 // time stamp
			+ 4 // change ID
			+ 1; // change type
		if (targetConfigAddress != null) {
			serializedSize += 2; // Depth
			for (ServerConfigElement el : targetConfigAddress)
				serializedSize += el.owner.serialize().size() + //
				1 // address length
				+ el.address.size();
			serializedSize++; // config change type
		}
		byte[] valueBytes = targetValue == null ? null : targetValue.getBytes(UTF_8);
		if (valueBytes != null) {
			serializedSize += 2 // value length
				+ valueBytes.length;
		}
		ByteArray targetRoleOwnerBytes = targetRoleOwner == null ? null : targetRoleOwner.serialize();
		if (targetRoleOwner != null)
			serializedSize += targetRoleOwnerBytes.size()//
			+ 8; // role ID
		ByteArray grantedBytes = grantedClient == null ? null : grantedClient.serialize();
		if (grantedClient != null)
			serializedSize += grantedBytes.size();
		ByteArray inhRoleOwnerBytes = inheritedRoleOwner == null ? null : inheritedRoleOwner.serialize();
		if (inheritedRoleOwner != null)
			serializedSize += inhRoleOwnerBytes.size() // algorithm name length
			+ 8; // role ID

		ByteBuffer buffer = ByteBuffer.allocate(serializedSize);
		buffer.putLong(timeStamp.getEpochSecond()).putInt(timeStamp.getNano())//
		.putInt(changeId)//
		.put((byte) type.ordinal());
		if (targetConfigAddress != null) {
			buffer.putInt(targetConfigAddress.size());
			for (ServerConfigElement addr : targetConfigAddress) {
				addr.owner.serialize().putInto(buffer);
				buffer.put((byte) addr.address.size());
				addr.address.putInto(buffer);
			}
		}
		if (targetValue != null)
			buffer.putShort((short) valueBytes.length).put(valueBytes);
		if (targetRoleOwner != null) {
			targetRoleOwnerBytes.putInto(buffer);
			buffer.putLong(targetRoleId);
		}
		if (grantedClient != null)
			grantedBytes.putInto(buffer);
		if (inheritedRoleOwner != null) {
			inhRoleOwnerBytes.putInto(buffer);
			buffer.putLong(inheritedRoleId);
		}
		return buffer.array();
	}

	public interface SignatureGenOrVal {
		byte[] sign(byte[] data);
	}

	public static ObservableServiceChange createConfigChange(ServiceObservableConfig config, ConfigModificationType type, String value,
		ObservableServiceClient actor, Instant timeStamp, int changeId, SignatureGenOrVal signature) {
		ServiceChangeType serviceType = null;
		switch (type) {
		case Add:
			serviceType = ServiceChangeType.ConfigAdd;
			break;
		case Modify:
			serviceType = ServiceChangeType.ConfigModify;
			break;
		case Rename:
			serviceType = ServiceChangeType.ConfigRename;
			break;
		case Delete:
			serviceType = ServiceChangeType.ConfigDelete;
			break;
		}
		if (serviceType == null)
			throw new IllegalStateException("Unrecognized modification type: " + type);

		byte[] encoded = encodeForSignature(timeStamp, changeId, serviceType, config.getAddressPath(), type, value, null, 0, null, null, 0);
		byte[] sign = signature.sign(encoded);
		return new ObservableServiceChange(actor, timeStamp, changeId, serviceType, config, type, value, null, null, null,
			new ByteArray(sign));
	}

	public static ObservableServiceChange createRoleAllowed(ServiceObservableConfig config, ObservableServiceRole role,
		ConfigModificationType permissionType, ObservableServiceClient actor, Instant timeStamp, int changeId,
		SignatureGenOrVal signature) {
		byte[] encoded = encodeForSignature(timeStamp, changeId, ServiceChangeType.RoleAllow, config.getAddressPath(), permissionType, null,
			null, 0, null, null, 0);
		byte[] sign = signature.sign(encoded);
		return new ObservableServiceChange(actor, timeStamp, changeId, ServiceChangeType.RoleAllow, config, permissionType, null, role,
			null, null, new ByteArray(sign));
	}

	public static ObservableServiceChange createRoleGranted(ObservableServiceRole role, ObservableServiceClient grantee,
		ObservableServiceClient actor, Instant timeStamp, int changeId, SignatureGenOrVal signature) {
		byte[] encoded = encodeForSignature(timeStamp, changeId, ServiceChangeType.RoleGrant, null, null, null, role.getOwner().getId(),
			role.getId(), grantee.getId(), null, 0);
		byte[] sign = signature.sign(encoded);
		return new ObservableServiceChange(actor, timeStamp, changeId, ServiceChangeType.RoleGrant, null, null, null, role, grantee, null,
			new ByteArray(sign));
	}

	public static ObservableServiceChange createRoleInherited(ObservableServiceRole inherited, ObservableServiceRole heir,
		ObservableServiceClient actor, Instant timeStamp, int changeId, SignatureGenOrVal signature) {
		byte[] encoded = encodeForSignature(timeStamp, changeId, ServiceChangeType.RoleInherit, null, null, null, heir.getOwner().getId(),
			heir.getId(), null, inherited.getOwner().getId(), inherited.getId());
		byte[] sign = signature.sign(encoded);
		return new ObservableServiceChange(actor, timeStamp, changeId, ServiceChangeType.RoleInherit, null, null, null, heir, null,
			inherited, new ByteArray(sign));
	}
}
