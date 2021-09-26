package ru.r2cloud.apt;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.compress.utils.IOUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import ru.r2cloud.apt.model.DebFile;

public class AptRepositoryTest {

	@Rule
	public TemporaryFolder tempFolder = new TemporaryFolder();

	@Test
	public void testSuccess() throws Exception {
		FileTransport transport = new FileTransport(tempFolder.getRoot().getAbsolutePath());
		AptRepository aptMan = new AptRepositoryImpl("codename", "component", null, transport);
		aptMan.saveFile(new DebFile(new File("src/test/resources/rtl-sdr_0.6git_armhf.deb")));
		assertFiles(new File("src/test/resources/expected"), tempFolder.getRoot());
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