package ru.r2cloud.apt;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.apache.commons.compress.utils.IOUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import ru.r2cloud.apt.model.DebFile;
import ru.r2cloud.apt.model.SignConfiguration;

public class AptRepositoryTest {

	@Rule
	public TemporaryFolder tempFolder = new TemporaryFolder();

	@Test
	public void testSuccess() throws Exception {
		FileTransport transport = new FileTransport(tempFolder.getRoot().getAbsolutePath());
		AptRepository aptMan = new AptRepositoryImpl("codename", "component", null, transport);
		aptMan.saveFiles(Collections.singletonList(new DebFile(new File("src/test/resources/rtl-sdr_0.6git_armhf.deb"))));
		assertFiles(new File("src/test/resources/expected"), tempFolder.getRoot());
	}

	@Test
	public void testCleanup() throws Exception {
		FileTransport transport = new FileTransport(tempFolder.getRoot().getAbsolutePath());
		AptRepository aptMan = new AptRepositoryImpl("codename", "component", null, transport);
		aptMan.saveFiles(Collections.singletonList(new DebFile(new File("src/test/resources/rtl-sdr_0.6git_armhf.deb"))));
		aptMan.saveFiles(Collections.singletonList(new DebFile(new File("src/test/resources/rtl-sdr_0.6_armhf.deb"))));
		aptMan.cleanup(2);
		assertFilesInDirectory(tempFolder.getRoot().getAbsolutePath() + File.separator + "pool" + File.separator + "component" + File.separator + "r" + File.separator + "rtl-sdr", "rtl-sdr_0.6git_armhf.deb", "rtl-sdr_0.6_armhf.deb");
		aptMan.cleanup(1);
		assertFilesInDirectory(tempFolder.getRoot().getAbsolutePath() + File.separator + "dists" + File.separator + "codename" + File.separator + "component" + File.separator + "binary-armhf" + File.separator + "by-hash" + File.separator + "MD5Sum", "65ebfe0e459b7c7a12d1584df17ff054",
				"4b21caa7442e0cae48c1d8485209a0b4");
		assertFilesInDirectory(tempFolder.getRoot().getAbsolutePath() + File.separator + "pool" + File.separator + "component" + File.separator + "r" + File.separator + "rtl-sdr", "rtl-sdr_0.6_armhf.deb");
	}

	@Test
	public void testDelete() throws Exception {
		FileTransport transport = new FileTransport(tempFolder.getRoot().getAbsolutePath());
		AptRepository aptMan = new AptRepositoryImpl("codename", "component", null, transport);
		aptMan.saveFiles(Collections.singletonList(new DebFile(new File("src/test/resources/rtl-sdr_0.6git_armhf.deb"))));
		aptMan.deletePackages(Collections.singleton("rtl-sdr"));
		assertDirectoryEmpty(tempFolder.getRoot().getAbsolutePath() + File.separator + "pool" + File.separator + "component" + File.separator + "r");
	}

	@Test(expected = IOException.class)
	public void testInvalidGpg() throws Exception {
		SignConfiguration config = new SignConfiguration();
		config.setGpgCommand(UUID.randomUUID().toString());
		config.setKeyname(UUID.randomUUID().toString());
		config.setPassphrase(UUID.randomUUID().toString());
		GpgSigner signer = new GpgSignerImpl(config);
		FileTransport transport = new FileTransport(tempFolder.getRoot().getAbsolutePath());
		AptRepository aptMan = new AptRepositoryImpl("codename", "component", signer, transport);
		aptMan.saveFiles(Collections.singletonList(new DebFile(new File("src/test/resources/rtl-sdr_0.6git_armhf.deb"))));
	}

	private static void assertDirectoryEmpty(String directory) {
		File dir = new File(directory);
		assertTrue(dir.isDirectory());
		assertEquals(0, dir.listFiles().length);
	}

	private static void assertFilesInDirectory(String basepath, String... filenames) {
		File base = new File(basepath);
		assertTrue(base.exists());
		assertTrue(base.isDirectory());
		File[] actualFiles = base.listFiles();
		assertEquals(filenames.length, actualFiles.length);
		Set<String> indexedNames = new HashSet<>();
		for (String cur : filenames) {
			indexedNames.add(cur);
		}
		for (File cur : actualFiles) {
			assertTrue("non existing file: " + cur.getName(), indexedNames.contains(cur.getName()));
		}
	}

	private static void assertFiles(File expected, File actual) {
		if (expected.isFile()) {
			if (expected.getName().equalsIgnoreCase("Release")) {
				try (BufferedReader expectedReader = new BufferedReader(new FileReader(expected)); BufferedReader actualReader = new BufferedReader(new FileReader(actual))) {
					while (true) {
						String expectedLine = expectedReader.readLine();
						String actualLine = actualReader.readLine();
						if (expectedLine == null) {
							assertNull(actualLine);
							break;
						}
						if (expectedLine.startsWith("Date:")) {
							// dates are always different
							continue;
						}
						assertEquals(expectedLine, actualLine);
					}
				} catch (IOException e) {
					fail("unable to read file: " + e.getMessage());
				}
			} else {
				assertBinaryFilesEqual(expected, actual);
			}
		}
		if (expected.isDirectory()) {
			assertDirectory(expected, actual);
		}
	}

	private static void assertBinaryFilesEqual(File expected, File actual) {
		try (InputStream is = new BufferedInputStream(new FileInputStream(expected)); InputStream ais = new BufferedInputStream(new FileInputStream(actual))) {
			byte[] expectedBody = IOUtils.toByteArray(is);
			byte[] actualBody = IOUtils.toByteArray(ais);
			assertArrayEquals(expectedBody, actualBody);
		} catch (IOException e) {
			fail("unable to read file: " + e.getMessage());
		}
	}

	private static void assertDirectory(File expected, File actual) {
		assertEquals(expected.isDirectory(), actual.isDirectory());
		File[] expectedFiles = expected.listFiles();
		File[] actualFiles = actual.listFiles();
		assertEquals(expectedFiles.length, actualFiles.length);
		for (int i = 0; i < expectedFiles.length; i++) {
			assertFiles(expectedFiles[i], actualFiles[i]);
		}
	}

}
