package ru.r2cloud.apt;

import java.io.File;

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
	}

}
