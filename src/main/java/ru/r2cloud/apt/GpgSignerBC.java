package ru.r2cloud.apt;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.Security;
import java.util.Iterator;
import java.util.Locale;

import org.bouncycastle.bcpg.ArmoredOutputStream;
import org.bouncycastle.bcpg.BCPGOutputStream;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPPrivateKey;
import org.bouncycastle.openpgp.PGPSecretKey;
import org.bouncycastle.openpgp.PGPSecretKeyRing;
import org.bouncycastle.openpgp.PGPSecretKeyRingCollection;
import org.bouncycastle.openpgp.PGPSignature;
import org.bouncycastle.openpgp.PGPSignatureGenerator;
import org.bouncycastle.openpgp.PGPSignatureSubpacketGenerator;
import org.bouncycastle.openpgp.PGPUtil;
import org.bouncycastle.openpgp.operator.jcajce.JcaKeyFingerprintCalculator;
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPContentSignerBuilder;
import org.bouncycastle.openpgp.operator.jcajce.JcePBESecretKeyDecryptorBuilder;

import ru.r2cloud.apt.model.Release;
import ru.r2cloud.apt.model.SignConfiguration;

public class GpgSignerBC implements GpgSigner {

	private final int algorithm;
	private final PGPSecretKey pgpSecKey;
	private final PGPPrivateKey pgpPrivKey;

	public GpgSignerBC(SignConfiguration signConfig) throws Exception {
		Security.addProvider(new BouncyCastleProvider());
		algorithm = convertHashAlgorithm(signConfig.getHashAlgorithm());
		pgpSecKey = readSecretKey(signConfig.getSecretKeyFilename(), signConfig.getKeyname().toLowerCase(Locale.UK));
		pgpPrivKey = pgpSecKey.extractPrivateKey(new JcePBESecretKeyDecryptorBuilder().setProvider(BouncyCastleProvider.PROVIDER_NAME).build(signConfig.getPassphrase().toCharArray()));
	}

	@Override
	public void signAndSave(String path, Release release, boolean clearsign, Transport transport) throws IOException {
		if (clearsign) {
			createClearsignAndSave(path, release, transport);
		} else {
			createDetachedSignatureAndSave(path, release, transport);
		}
	}

	private void createClearsignAndSave(String path, Release release, Transport transport) throws IOException {
		PGPSignatureGenerator sGen = new PGPSignatureGenerator(new JcaPGPContentSignerBuilder(pgpSecKey.getPublicKey().getAlgorithm(), algorithm).setProvider(BouncyCastleProvider.PROVIDER_NAME));
		PGPSignatureSubpacketGenerator spGen = new PGPSignatureSubpacketGenerator();
		try {
			sGen.init(PGPSignature.CANONICAL_TEXT_DOCUMENT, pgpPrivKey);
		} catch (PGPException e) {
			throw new IOException("unable to initialize PGP signer", e);
		}
		Iterator<String> it = pgpSecKey.getPublicKey().getUserIDs();
		if (it.hasNext()) {
			spGen.addSignerUserID(false, it.next());
			sGen.setHashedSubpackets(spGen.generate());
		}
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		release.save(baos);
		baos.close();
		transport.save(path, new IOCallback() {

			@Override
			public void save(OutputStream os) throws IOException {
				ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
				ArmoredOutputStream aOut = new ArmoredOutputStream(os);
				aOut.beginClearText(algorithm);
				//
				// note the last \n/\r/\r\n in the file is ignored
				//
				ByteArrayOutputStream lineOut = new ByteArrayOutputStream();
				int lookAhead = readInputLine(lineOut, bais);
				processLine(aOut, sGen, lineOut.toByteArray());
				if (lookAhead != -1) {
					do {
						lookAhead = readInputLine(lineOut, lookAhead, bais);
						sGen.update((byte) '\r');
						sGen.update((byte) '\n');
						processLine(aOut, sGen, lineOut.toByteArray());
					} while (lookAhead != -1);
				}
				bais.close();
				aOut.endClearText();
				try (BCPGOutputStream bOut = new BCPGOutputStream(aOut)) {
					sGen.generate().encode(bOut);
				} catch (PGPException e) {
					throw new IOException("unable to generate signature", e);
				}
			}

			@Override
			public void load(InputStream is) throws IOException {
				// do nothing
			}
		});
	}

	private void createDetachedSignatureAndSave(String path, Release release, Transport transport) throws IOException {
		PGPSignatureGenerator sGen = new PGPSignatureGenerator(new JcaPGPContentSignerBuilder(pgpSecKey.getPublicKey().getAlgorithm(), algorithm).setProvider(BouncyCastleProvider.PROVIDER_NAME));
		try {
			sGen.init(PGPSignature.BINARY_DOCUMENT, pgpPrivKey);
		} catch (PGPException e1) {
			throw new IOException("unable to initialize PGP signer", e1);
		}
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		release.save(baos);
		baos.close();
		sGen.update(baos.toByteArray());
		transport.save(path, new IOCallback() {
			@Override
			public void save(OutputStream os) throws IOException {
				try (BCPGOutputStream bOut = new BCPGOutputStream(new ArmoredOutputStream(os))) {
					sGen.generate().encode(bOut);
				} catch (PGPException e) {
					throw new IOException("unable to generate signature", e);
				}
			}

			@Override
			public void load(InputStream is) throws IOException {
				// do nothing
			}
		});
	}

	private static void processLine(OutputStream aOut, PGPSignatureGenerator sGen, byte[] line) throws IOException {
		// note: trailing white space needs to be removed from the end of
		// each line for signature calculation RFC 4880 Section 7.1
		int length = getLengthWithoutWhiteSpace(line);
		if (length > 0) {
			sGen.update(line, 0, length);
		}
		aOut.write(line, 0, line.length);
	}

	private static boolean isLineEnding(byte b) {
		return b == '\r' || b == '\n';
	}

	private static int getLengthWithoutWhiteSpace(byte[] line) {
		int end = line.length - 1;
		while (end >= 0 && isWhiteSpace(line[end])) {
			end--;
		}
		return end + 1;
	}

	private static boolean isWhiteSpace(byte b) {
		return isLineEnding(b) || b == '\t' || b == ' ';
	}

	private static int readInputLine(ByteArrayOutputStream bOut, InputStream fIn) throws IOException {
		bOut.reset();
		int lookAhead = -1;
		int ch;
		while ((ch = fIn.read()) >= 0) {
			bOut.write(ch);
			if (ch == '\r' || ch == '\n') {
				lookAhead = readPassedEOL(bOut, ch, fIn);
				break;
			}
		}
		return lookAhead;
	}

	private static int readInputLine(ByteArrayOutputStream bOut, int lookAhead, InputStream fIn) throws IOException {
		bOut.reset();
		int ch = lookAhead;
		do {
			bOut.write(ch);
			if (ch == '\r' || ch == '\n') {
				lookAhead = readPassedEOL(bOut, ch, fIn);
				break;
			}
		} while ((ch = fIn.read()) >= 0);
		if (ch < 0) {
			lookAhead = -1;
		}
		return lookAhead;
	}

	private static int readPassedEOL(ByteArrayOutputStream bOut, int lastCh, InputStream fIn) throws IOException {
		int lookAhead = fIn.read();
		if (lastCh == '\r' && lookAhead == '\n') {
			bOut.write(lookAhead);
			lookAhead = fIn.read();
		}
		return lookAhead;
	}

	private static PGPSecretKey readSecretKey(String fileName, String publicKeyId) throws IOException, PGPException {
		try (InputStream keyIn = new BufferedInputStream(new FileInputStream(fileName))) {
			PGPSecretKeyRingCollection pgpSec = new PGPSecretKeyRingCollection(PGPUtil.getDecoderStream(keyIn), new JcaKeyFingerprintCalculator());
			Iterator<PGPSecretKeyRing> it = pgpSec.getKeyRings();
			while (it.hasNext()) {
				PGPSecretKeyRing keyRing = it.next();
				Iterator<PGPSecretKey> keyIter = keyRing.getSecretKeys();
				while (keyIter.hasNext()) {
					PGPSecretKey key = keyIter.next();
					if (!key.isSigningKey()) {
						continue;
					}
					String currentKeyId = Long.toHexString(key.getPublicKey().getKeyID());
					if (currentKeyId.endsWith(publicKeyId)) {
						return key;
					}
				}
			}
		}
		throw new IllegalArgumentException("Can't find signing key in key ring: " + publicKeyId);
	}

	private static int convertHashAlgorithm(String digestName) {
		if (digestName.equals("SHA256")) {
			return PGPUtil.SHA256;
		} else if (digestName.equals("SHA384")) {
			return PGPUtil.SHA384;
		} else if (digestName.equals("SHA512")) {
			return PGPUtil.SHA512;
		} else if (digestName.equals("MD5")) {
			return PGPUtil.MD5;
		} else if (digestName.equals("RIPEMD160")) {
			return PGPUtil.RIPEMD160;
		} else {
			throw new IllegalArgumentException("unsupported algorithm: " + digestName);
		}
	}
}
