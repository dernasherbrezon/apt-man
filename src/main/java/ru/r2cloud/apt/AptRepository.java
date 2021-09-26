package ru.r2cloud.apt;

import java.io.IOException;
import java.util.List;

import ru.r2cloud.apt.model.DebFile;

/**
 * Main entry point to the library. Typical program include:
 * <pre>
 * {@code
 * GpgSigner signer = new GpgSignerImpl(config);
 * Transport transport = new FileTransport("./apt-basedir/");
 * AptRepository repository = new AptRepositoryImpl(codename, component, signer, transport);
 * repository.saveFile(new DebFile(new File("some.deb")));
 * }
 * </pre>
 * 
 * GpgSigner can be empty. In that case no gpg signature will be generated.
 * 
 * @author dernasherbrezon
 *
 */
public interface AptRepository {

	/**
	 * Save multiple .deb files into apt repository. The version, architecture and the package name extracted from the .deb file.
	 * 
	 * @param debFiles - list of .deb files. Create .deb file meta information using <code>DebFile file = new DebFile(new File("some.deb")</code>
	 * @throws IOException - on any error
	 */
	void saveFiles(List<DebFile> debFiles) throws IOException;

	/**
	 * Convenient method for saving single file. Under the hood it will call {@link ru.r2cloud.apt.AptRepository#saveFiles(List) }
	 * @param debFile - .deb file to save into apt repository
	 * @throws IOException - on any error
	 */
	void saveFile(DebFile debFile) throws IOException;

}
