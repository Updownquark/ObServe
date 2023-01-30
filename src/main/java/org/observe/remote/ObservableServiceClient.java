package org.observe.remote;

import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class ObservableServiceClient implements Comparable<ObservableServiceClient> {
	public static class ClientId implements Comparable<ClientId> {
		public final String keyAlgorithm;
		public final ByteArray publicKey;
		public final String signatureAlgorithm;

		private ByteArray theSerialized;

		public ClientId(String keyAlgorithm, ByteArray publicKey, String signatureAlgorithm) {
			this.keyAlgorithm = keyAlgorithm;
			this.publicKey = publicKey;
			this.signatureAlgorithm = signatureAlgorithm;
		}

		public ByteArray serialize() {
			if (theSerialized == null) {
				byte[] keyAlgBytes = keyAlgorithm.getBytes(ObservableServiceChange.UTF_8);
				byte[] sigAlgBytes = signatureAlgorithm.getBytes(ObservableServiceChange.UTF_8);
				byte[] serialized = new byte[1 // Key alg length
				                             + keyAlgBytes.length//
				                             + 1 // key size
				                             + publicKey.size()//
				                             + 1 // Signature alg length
				                             + sigAlgBytes.length];
				serialized[0] = (byte) keyAlgBytes.length;
				System.arraycopy(keyAlgBytes, 0, serialized, 1, keyAlgBytes.length);
				serialized[keyAlgBytes.length + 1] = (byte) (publicKey.size() / 8);
				publicKey.copy(serialized, keyAlgBytes.length + 2);
				serialized[keyAlgBytes.length + publicKey.size() + 2] = (byte) sigAlgBytes.length;
				System.arraycopy(sigAlgBytes, 0, serialized, keyAlgBytes.length + publicKey.size() + 3, sigAlgBytes.length);
				theSerialized = new ByteArray(serialized);
			}
			return theSerialized;
		}

		@Override
		public int compareTo(ClientId o) {
			int comp = keyAlgorithm.compareTo(o.keyAlgorithm);
			if (comp == 0)
				comp = publicKey.compareTo(o.publicKey);
			return comp;
		}

		@Override
		public int hashCode() {
			return Objects.hash(keyAlgorithm, publicKey);
		}

		@Override
		public boolean equals(Object obj) {
			return obj instanceof ClientId && keyAlgorithm.equals(((ClientId) obj).keyAlgorithm)//
				&& publicKey.equals(((ClientId) obj).publicKey);
		}

		@Override
		public String toString() {
			return keyAlgorithm + ":" + publicKey;
		}
	}

	static final ObservableServiceClient ALL = new ObservableServiceClient();

	private final ClientId theId;
	private final PublicKey thePublicKey;
	private final String theSignatureAlgorithm;
	private final ObservableServiceRole theSelfRole;
	private final Map<Long, ObservableServiceRole> theDefinedRoles;
	private final Set<ObservableServiceRole> theAssignedRoles;
	private final ByteArray theEncodedId;

	private ObservableServiceClient() {
		theId = new ClientId("", new ByteArray(new byte[0]), "");
		thePublicKey = null;
		theSignatureAlgorithm = null;
		theSelfRole = null;
		theDefinedRoles = null;
		theAssignedRoles = null;
		theEncodedId = new ByteArray(new byte[2]);
	}

	public ObservableServiceClient(PublicKey publicKey, String signatureAlgorithm) throws NoSuchAlgorithmException, InvalidKeyException {
		theId = new ClientId(publicKey.getAlgorithm(), new ByteArray(publicKey.getEncoded()), signatureAlgorithm);
		thePublicKey = publicKey;
		theSignatureAlgorithm = signatureAlgorithm;
		theSelfRole = new ObservableServiceRole(this, 0, "self");
		theDefinedRoles = new LinkedHashMap<>();
		theDefinedRoles.put(theSelfRole.getId(), theSelfRole);
		theAssignedRoles = new LinkedHashSet<>();
		theAssignedRoles.add(theSelfRole);

		// Ensure we can verify signatures
		Signature signature = Signature.getInstance(theSignatureAlgorithm);
		signature.initVerify(thePublicKey);
		byte[] algBytes = theId.keyAlgorithm.getBytes();
		byte[] encodedId = new byte[1 + algBytes.length + 1 + theId.publicKey.size()];
		encodedId[0] = (byte) algBytes.length;
		System.arraycopy(algBytes, 0, encodedId, 1, algBytes.length);
		byte keyLengthByte = (byte) (theId.publicKey.size() / 8); // Assume it's a multiple of 8 bytes
		encodedId[algBytes.length + 1] = keyLengthByte;
		theId.publicKey.copy(encodedId, 1 + algBytes.length + 1, theId.publicKey.size());
		theEncodedId = new ByteArray(encodedId);
	}

	public ClientId getId() {
		return theId;
	}

	public PublicKey getPublicKey() {
		return thePublicKey;
	}

	public String getSignatureAlgorithm() {
		return theSignatureAlgorithm;
	}

	public ObservableServiceRole getSelfRole() {
		return theSelfRole;
	}

	public Map<Long, ObservableServiceRole> getDefinedRoles() {
		return Collections.unmodifiableMap(theDefinedRoles);
	}

	public ObservableServiceRole getOrCreateRole(long roleId, String roleName) {
		return theDefinedRoles.computeIfAbsent(roleId, __ -> new ObservableServiceRole(this, roleId, roleName));
	}

	public Set<ObservableServiceRole> getAssignedRoles() {
		return Collections.unmodifiableSet(theAssignedRoles);
	}

	public boolean isGranted(ObservableServiceRole role) {
		if (role == ObservableServiceRole.ALL)
			return true;
		else if (role.getOwner() == this)
			return true;
		for (ObservableServiceRole assigned : theAssignedRoles) {
			if (assigned.inherits(role))
				return true;
		}
		return false;
	}

	public void grant(ObservableServiceRole role) {
		theAssignedRoles.add(role);
	}

	public ByteArray getEncodedId() {
		return theEncodedId;
	}

	public boolean verifySignature(byte[] data, byte[] signature) throws SignatureException {
		Signature sig;
		try {
			sig = Signature.getInstance(theSignatureAlgorithm);
			sig.initVerify(thePublicKey);
		} catch (NoSuchAlgorithmException | InvalidKeyException e) {
			throw new IllegalStateException("Why wasn't this caught in the constructor?", e);
		}
		sig.update(data);
		return sig.verify(signature);
	}

	@Override
	public int compareTo(ObservableServiceClient o) {
		return theId.compareTo(o.theId);
	}

	public static PublicKey decodePublicKey(String algorithm, byte[] encoded) throws NoSuchAlgorithmException, InvalidKeySpecException {
		X509EncodedKeySpec spec = new X509EncodedKeySpec(encoded);
		KeyFactory factory = KeyFactory.getInstance(algorithm);
		return factory.generatePublic(spec);
	}
}
