package ru.r2cloud.apt;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.security.Security;
import java.util.Iterator;
import java.util.Locale;

import org.apache.commons.io.IOUtils;
import org.bouncycastle.bcpg.ArmoredInputStream;
import org.bouncycastle.bcpg.ArmoredOutputStream;
import org.bouncycastle.bcpg.BCPGOutputStream;
import org.bouncycastle.bcpg.HashAlgorithmTags;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openpgp.PGPCompressedData;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPPrivateKey;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPSecretKey;
import org.bouncycastle.openpgp.PGPSecretKeyRing;
import org.bouncycastle.openpgp.PGPSecretKeyRingCollection;
import org.bouncycastle.openpgp.PGPSignature;
import org.bouncycastle.openpgp.PGPSignatureGenerator;
import org.bouncycastle.openpgp.PGPSignatureList;
import org.bouncycastle.openpgp.PGPSignatureSubpacketGenerator;
import org.bouncycastle.openpgp.PGPUtil;
import org.bouncycastle.openpgp.jcajce.JcaPGPObjectFactory;
import org.bouncycastle.openpgp.operator.jcajce.JcaKeyFingerprintCalculator;
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPContentSignerBuilder;
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPContentVerifierBuilderProvider;
import org.bouncycastle.openpgp.operator.jcajce.JcePBESecretKeyDecryptorBuilder;
import org.bouncycastle.util.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ru.r2cloud.apt.model.Release;
import ru.r2cloud.apt.model.SignConfiguration;

public class GpgSignerBC implements GpgSigner {

	private static final Logger LOG = LoggerFactory.getLogger(GpgSignerBC.class);

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

	@Override
	public boolean validate(String path, Release release, boolean clearsign, Transport transport) throws IOException, ResourceDoesNotExistException {
		final ByteArrayOutputStream baos = new ByteArrayOutputStream();
		transport.load(path, new IOCallback() {

			@Override
			public void save(OutputStream os) throws IOException {
				// do nothing
			}

			@Override
			public void load(InputStream is) throws IOException {
				IOUtils.copy(is, baos);
			}
		});
		baos.close();

		if (clearsign) {
			return verifyClearsign(baos.toByteArray(), release);
		} else {
			return verifyDetached(baos.toByteArray(), release);
		}
	}

	private boolean verifyClearsign(byte[] clearsignedText, Release release) throws IOException {
		ArmoredInputStream aIn = new ArmoredInputStream(new ByteArrayInputStream(clearsignedText));
		ByteArrayOutputStream clearText = new ByteArrayOutputStream();

		//
		// write out signed section using the local line separator.
		// note: trailing white space needs to be removed from the end of
		// each line RFC 4880 Section 7.1
		//
		ByteArrayOutputStream lineOut = new ByteArrayOutputStream();
		int lookAhead = readInputLine(lineOut, aIn);
		byte[] lineSep = getLineSeparator();

		if (lookAhead != -1 && aIn.isClearText()) {
			byte[] line = lineOut.toByteArray();
			clearText.write(line, 0, getLengthWithoutSeparatorOrTrailingWhitespace(line));
			clearText.write(lineSep);

			while (lookAhead != -1 && aIn.isClearText()) {
				lookAhead = readInputLine(lineOut, lookAhead, aIn);

				line = lineOut.toByteArray();
				clearText.write(line, 0, getLengthWithoutSeparatorOrTrailingWhitespace(line));
				clearText.write(lineSep);
			}
		} else {
			// a single line file
			if (lookAhead != -1) {
				byte[] line = lineOut.toByteArray();
				clearText.write(line, 0, getLengthWithoutSeparatorOrTrailingWhitespace(line));
				clearText.write(lineSep);
			}
		}
		clearText.close();

		boolean clearsignIsTheSame = verifyClearTextAndRelease(clearText.toByteArray(), release);
		if (!clearsignIsTheSame) {
			return false;
		}

		try {
			JcaPGPObjectFactory pgpFact = new JcaPGPObjectFactory(aIn);
			PGPSignatureList p3 = (PGPSignatureList) pgpFact.nextObject();
			PGPSignature sig = p3.get(0);
			PGPPublicKey publicKey = pgpSecKey.getPublicKey();
			sig.init(new JcaPGPContentVerifierBuilderProvider().setProvider("BC"), publicKey);

			//
			// read the input, making sure we ignore the last newline.
			//
			InputStream sigIn = new BufferedInputStream(new ByteArrayInputStream(clearText.toByteArray()));
			lookAhead = readInputLine(lineOut, sigIn);
			processLine(sig, lineOut.toByteArray());
			if (lookAhead != -1) {
				do {
					lookAhead = readInputLine(lineOut, lookAhead, sigIn);
					sig.update((byte) '\r');
					sig.update((byte) '\n');
					processLine(sig, lineOut.toByteArray());
				} while (lookAhead != -1);
			}
			sigIn.close();
			return sig.verify();
		} catch (PGPException e) {
			LOG.error("unable to verify", e);
			return false;
		}
	}

	private static boolean verifyClearTextAndRelease(byte[] cleartext, Release release) {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		try {
			release.save(baos);
		} catch (IOException e) {
			LOG.error("unable to save into byte array", e);
			return false;
		} finally {
			try {
				baos.close();
			} catch (IOException e) {
				LOG.error("unable to close", e);
			}
		}
		try (BufferedReader clearBuffer = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(cleartext))); BufferedReader releaseBuffer = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(baos.toByteArray())));) {
			String clearLine = null;
			String releaseLine = null;
			while ((clearLine = clearBuffer.readLine()) != null && (releaseLine = releaseBuffer.readLine()) != null) {
				if (!clearLine.equals(releaseLine)) {
					return false;
				}
			}
		} catch (Exception e) {
			LOG.error("unable to compare buffers", e);
			return false;
		}
		return true;
	}

	private boolean verifyDetached(byte[] inBytes, Release release) throws IOException {
		try {
			InputStream in = PGPUtil.getDecoderStream(new ByteArrayInputStream(inBytes));
			JcaPGPObjectFactory pgpFact = new JcaPGPObjectFactory(in);
			PGPSignatureList p3;
			Object o = pgpFact.nextObject();
			if (o instanceof PGPCompressedData) {
				PGPCompressedData c1 = (PGPCompressedData) o;
				pgpFact = new JcaPGPObjectFactory(c1.getDataStream());
				p3 = (PGPSignatureList) pgpFact.nextObject();
			} else {
				p3 = (PGPSignatureList) o;
			}

			ByteArrayOutputStream releaseBaos = new ByteArrayOutputStream();
			release.save(releaseBaos);
			releaseBaos.close();
			InputStream dIn = new BufferedInputStream(new ByteArrayInputStream(releaseBaos.toByteArray()));
			PGPSignature sig = p3.get(0);
			PGPPublicKey key = pgpSecKey.getPublicKey();
			sig.init(new JcaPGPContentVerifierBuilderProvider().setProvider("BC"), key);
			int ch;
			while ((ch = dIn.read()) >= 0) {
				sig.update((byte) ch);
			}
			dIn.close();
			return sig.verify();
		} catch (PGPException e) {
			LOG.error("unable to verify", e);
			return false;
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

	private static void processLine(PGPSignature sig, byte[] line) {
		int length = getLengthWithoutWhiteSpace(line);
		if (length > 0) {
			sig.update(line, 0, length);
		}
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

	private static byte[] getLineSeparator() {
		String nl = Strings.lineSeparator();
		byte[] nlBytes = new byte[nl.length()];
		for (int i = 0; i != nlBytes.length; i++) {
			nlBytes[i] = (byte) nl.charAt(i);
		}
		return nlBytes;
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

	private static int getLengthWithoutSeparatorOrTrailingWhitespace(byte[] line) {
		int end = line.length - 1;
		while (end >= 0 && isWhiteSpace(line[end])) {
			end--;
		}
		return end + 1;
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
			return HashAlgorithmTags.SHA256;
		} else if (digestName.equals("SHA384")) {
			return HashAlgorithmTags.SHA384;
		} else if (digestName.equals("SHA512")) {
			return HashAlgorithmTags.SHA512;
		} else if (digestName.equals("MD5")) {
			return HashAlgorithmTags.MD5;
		} else if (digestName.equals("RIPEMD160")) {
			return HashAlgorithmTags.RIPEMD160;
		} else {
			throw new IllegalArgumentException("unsupported algorithm: " + digestName);
		}
	}
}
