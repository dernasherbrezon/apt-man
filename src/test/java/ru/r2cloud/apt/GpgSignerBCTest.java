package ru.r2cloud.apt;

import static org.junit.Assert.assertTrue;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Security;
import java.util.Date;
import java.util.UUID;

import org.bouncycastle.bcpg.ArmoredOutputStream;
import org.bouncycastle.bcpg.CompressionAlgorithmTags;
import org.bouncycastle.bcpg.HashAlgorithmTags;
import org.bouncycastle.bcpg.PublicKeyAlgorithmTags;
import org.bouncycastle.bcpg.SymmetricKeyAlgorithmTags;
import org.bouncycastle.bcpg.sig.Features;
import org.bouncycastle.bcpg.sig.KeyFlags;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openpgp.PGPKeyPair;
import org.bouncycastle.openpgp.PGPKeyRingGenerator;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPSecretKeyRing;
import org.bouncycastle.openpgp.PGPSignature;
import org.bouncycastle.openpgp.PGPSignatureSubpacketGenerator;
import org.bouncycastle.openpgp.operator.PBESecretKeyEncryptor;
import org.bouncycastle.openpgp.operator.PGPContentSignerBuilder;
import org.bouncycastle.openpgp.operator.PGPDigestCalculator;
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPContentSignerBuilder;
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPDigestCalculatorProviderBuilder;
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPKeyPair;
import org.bouncycastle.openpgp.operator.jcajce.JcePBESecretKeyEncryptorBuilder;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import ru.r2cloud.apt.model.Release;
import ru.r2cloud.apt.model.SignConfiguration;

public class GpgSignerBCTest {

	@Rule
	public TemporaryFolder tempFolder = new TemporaryFolder();

	private String passphrase;
	private File secretKey;
	private String keyid;

	private static final int SIG_HASH = HashAlgorithmTags.SHA512;
	private static final int[] HASH_PREFERENCES = new int[] { HashAlgorithmTags.SHA512, HashAlgorithmTags.SHA384, HashAlgorithmTags.SHA256, HashAlgorithmTags.SHA224 };
	private static final int[] SYM_PREFERENCES = new int[] { SymmetricKeyAlgorithmTags.AES_256, SymmetricKeyAlgorithmTags.AES_192, SymmetricKeyAlgorithmTags.AES_128 };
	private static final int[] COMP_PREFERENCES = new int[] { CompressionAlgorithmTags.ZLIB, CompressionAlgorithmTags.BZIP2, CompressionAlgorithmTags.ZLIB, CompressionAlgorithmTags.UNCOMPRESSED };

	@Test
	public void testSuccess() throws Exception {
		SignConfiguration config = new SignConfiguration();
		config.setHashAlgorithm("SHA512");
		config.setKeyname(keyid);
		config.setPassphrase(passphrase);
		config.setSecretKeyFilename(secretKey.getAbsolutePath());
		GpgSignerBC signer = new GpgSignerBC(config);

		Release release = new Release();
		release.load(ReleaseTest.class.getClassLoader().getResourceAsStream("Release"));

		signer.signAndSave("Release.gpg", release, false, new FileTransport(tempFolder.getRoot().getAbsolutePath()));
		assertTrue(new File(tempFolder.getRoot(), "Release.gpg").exists());
		signer.signAndSave("InRelease", release, true, new FileTransport(tempFolder.getRoot().getAbsolutePath()));
		assertTrue(new File(tempFolder.getRoot(), "InRelease").exists());
	}

	@Before
	public void start() throws Exception {
		Security.addProvider(new BouncyCastleProvider());
		passphrase = UUID.randomUUID().toString();
		secretKey = new File(tempFolder.getRoot(), UUID.randomUUID().toString());
		try (OutputStream os = new BufferedOutputStream(new FileOutputStream(secretKey))) {
			keyid = generateSecretKey(os, "info@example.com", passphrase.toCharArray());
		}
	}

	private static String generateSecretKey(OutputStream secretOut, String identity, char[] passPhrase) throws Exception {
		secretOut = new ArmoredOutputStream(secretOut);

		PGPDigestCalculator sha1Calc = new JcaPGPDigestCalculatorProviderBuilder().build().get(HashAlgorithmTags.SHA1);
		KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA", BouncyCastleProvider.PROVIDER_NAME);

		PGPContentSignerBuilder contentSignerBuilder = new JcaPGPContentSignerBuilder(PublicKeyAlgorithmTags.RSA_GENERAL, SIG_HASH).setProvider(BouncyCastleProvider.PROVIDER_NAME);
		PBESecretKeyEncryptor secretKeyEncryptor = new JcePBESecretKeyEncryptorBuilder(SymmetricKeyAlgorithmTags.AES_256, sha1Calc).setProvider(BouncyCastleProvider.PROVIDER_NAME).build(passPhrase);

		Date now = new Date();

		kpg.initialize(3072);
		KeyPair primaryKP = kpg.generateKeyPair();
		PGPKeyPair primaryKey = new JcaPGPKeyPair(PGPPublicKey.RSA_GENERAL, primaryKP, now);
		PGPSignatureSubpacketGenerator primarySubpackets = new PGPSignatureSubpacketGenerator();
		primarySubpackets.setKeyFlags(true, KeyFlags.CERTIFY_OTHER);
		primarySubpackets.setPreferredHashAlgorithms(false, HASH_PREFERENCES);
		primarySubpackets.setPreferredSymmetricAlgorithms(false, SYM_PREFERENCES);
		primarySubpackets.setPreferredCompressionAlgorithms(false, COMP_PREFERENCES);
		primarySubpackets.setFeature(false, Features.FEATURE_MODIFICATION_DETECTION);
		primarySubpackets.setIssuerFingerprint(false, primaryKey.getPublicKey());

		kpg.initialize(3072);
		KeyPair signingKP = kpg.generateKeyPair();
		PGPKeyPair signingKey = new JcaPGPKeyPair(PGPPublicKey.RSA_GENERAL, signingKP, now);
		String result = Long.toHexString(signingKey.getPrivateKey().getKeyID());
		PGPSignatureSubpacketGenerator signingKeySubpacket = new PGPSignatureSubpacketGenerator();
		signingKeySubpacket.setKeyFlags(true, KeyFlags.SIGN_DATA);
		signingKeySubpacket.setIssuerFingerprint(false, primaryKey.getPublicKey());

		kpg.initialize(3072);
		KeyPair encryptionKP = kpg.generateKeyPair();
		PGPKeyPair encryptionKey = new JcaPGPKeyPair(PGPPublicKey.RSA_GENERAL, encryptionKP, now);
		PGPSignatureSubpacketGenerator encryptionKeySubpackets = new PGPSignatureSubpacketGenerator();
		encryptionKeySubpackets.setKeyFlags(true, KeyFlags.ENCRYPT_COMMS | KeyFlags.ENCRYPT_STORAGE);
		encryptionKeySubpackets.setIssuerFingerprint(false, primaryKey.getPublicKey());

		PGPKeyRingGenerator gen = new PGPKeyRingGenerator(PGPSignature.POSITIVE_CERTIFICATION, primaryKey, identity, sha1Calc, primarySubpackets.generate(), null, contentSignerBuilder, secretKeyEncryptor);
		gen.addSubKey(signingKey, signingKeySubpacket.generate(), null, contentSignerBuilder);
		gen.addSubKey(encryptionKey, encryptionKeySubpackets.generate(), null);

		PGPSecretKeyRing secretKeys = gen.generateSecretKeyRing();
		secretKeys.encode(secretOut);

		secretOut.close();
		return result;
	}

}
