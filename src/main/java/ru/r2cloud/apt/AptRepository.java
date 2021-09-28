package ru.r2cloud.apt;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import ru.r2cloud.apt.model.DebFile;

/**
 * Main entry point to the library. Typical program include:
 * <pre>
 * {@code
 * GpgSigner signer = new GpgSignerImpl(config);
 * Transport transport = new FileTransport("./apt-basedir/");
 * AptRepository repository = new AptRepositoryImpl(codename, component, signer, transport);
 * repository.saveFiles(Collections.singletonList(new DebFile(new File("some.deb"))));
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
	 * Remove unused files from the repository.
	 * @param keepLast - keep last number of files in each category
	 * @throws IOException - on any error
	 */
	void cleanup(int keepLast) throws IOException;
	
	/**
	 * Delete packages and update the index.
	 * 
	 * @param packages - packages to remove
	 * @throws IOException - on any error
	 */
	void deletePackages(Set<String> packages) throws IOException;

}
