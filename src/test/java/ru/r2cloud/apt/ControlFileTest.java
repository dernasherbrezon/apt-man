package ru.r2cloud.apt;

import static org.junit.Assert.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;

import org.apache.commons.compress.utils.IOUtils;
import org.junit.Test;

import ru.r2cloud.apt.model.Architecture;
import ru.r2cloud.apt.model.ControlFile;
import ru.r2cloud.apt.model.DebFile;

public class ControlFileTest {

	@Test
	public void testNoSpaceAfterDescription() throws Exception {
		ControlFile controlFile = new ControlFile();
		controlFile.load(loadFile("control.txt"));
		controlFile.append("Filename: /dist/file.gz");
		assertEquals(loadFile("expectedControl.txt"), controlFile.getContents());
	}

	@Test
	public void testSupportZstd() throws Exception {
		DebFile file = new DebFile(new File("src/test/resources/libiio0_0.23-2_amd64.deb"));
		ControlFile control = file.getControl();
		assertEquals("0.23-2", control.getVersion());
		assertEquals(Architecture.AMD64, control.getArch());
		assertEquals("libiio0", control.getPackageName());
	}

	private static String loadFile(String name) throws IOException, UnsupportedEncodingException {
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		IOUtils.copy(ControlFileTest.class.getClassLoader().getResourceAsStream(name), outputStream);
		String contentString = outputStream.toString("UTF-8");
		outputStream.close();
		return contentString;
	}

}
