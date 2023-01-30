package org.observe.remote;

import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.SignatureException;

public class LocalObservableClient extends ObservableServiceClient {
	private final PrivateKey thePrivateKey;
	private final SecureRandom theRandom;

	private LocalObservableClient(KeyPair keyPair, SecureRandom random, String signatureAlgorithm)
		throws NoSuchAlgorithmException, NoSuchProviderException, InvalidKeyException {
		super(keyPair.getPublic(), signatureAlgorithm);
		thePrivateKey = keyPair.getPrivate();
		theRandom = random;

		// Ensure we can sign stuff
		Signature signature = Signature.getInstance(getSignatureAlgorithm());
		signature.initSign(thePrivateKey, theRandom);
	}

	public Signature createSignature() {
		Signature signature;
		try {
			signature = Signature.getInstance(getSignatureAlgorithm());
			signature.initSign(thePrivateKey, theRandom);
		} catch (NoSuchAlgorithmException | InvalidKeyException e) {
			throw new IllegalStateException("Why wasn't this caught in the constructor?", e);
		}
		return signature;
	}

	public byte[] sign(byte[] data) throws SignatureException {
		return sign(data, 0, data.length);
	}

	public byte[] sign(byte[] data, int offset, int length) throws SignatureException {
		Signature signature = createSignature();

		signature.update(data, offset, length);

		return signature.sign();
	}

	public static LocalObservableClient create() {
		try {
			return create("DSA", "SHA1PRNG", "SHA1withDSA");
		} catch (NoSuchAlgorithmException | NoSuchProviderException | InvalidKeyException e) {
			throw new IllegalStateException("Could not generate new Observable Client", e);
		}
	}

	public static LocalObservableClient create(String keyAlgorithm, String randomAlgorithm, String signatureAlgorithm)
		throws NoSuchAlgorithmException, InvalidKeyException, NoSuchProviderException {
		KeyPairGenerator keyGen;
		SecureRandom random;

		keyGen = KeyPairGenerator.getInstance("DSA");
		random = SecureRandom.getInstance("SHA1PRNG");
		keyGen.initialize(1024, random);

		return new LocalObservableClient(keyGen.generateKeyPair(), random, signatureAlgorithm);
	}
}
