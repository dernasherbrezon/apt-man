package ru.r2cloud.apt.model;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.zip.GZIPInputStream;

import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.compress.archivers.ar.ArArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream;
import org.apache.commons.compress.utils.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DebFile {

	private static final Logger LOG = LoggerFactory.getLogger(DebFile.class);

	private ControlFile control;
	private File file;
	private FileInfo info;

	public DebFile() {
		// do nothing
	}

	public DebFile(File file) throws IOException, ArchiveException {
		this.file = file;
		this.control = readControl(file);
		if (this.control == null) {
			throw new IOException("control file cannot be found");
		}
		this.info = calculateFileInfo(file);
	}

	private static FileInfo calculateFileInfo(File file2) throws IOException {
		FileInfo result = new FileInfo();
		result.setSize(String.valueOf(file2.length()));
		try (InputStream is = new FileInputStream(file2)) {
			result.load(is);
		}
		return result;
	}

	private static ControlFile readControl(File file) throws IOException, ArchiveException, UnsupportedEncodingException, FileNotFoundException {
		ArArchiveEntry entry;
		TarArchiveEntry controlEntry;
		try (ArchiveInputStream debStream = new ArchiveStreamFactory().createArchiveInputStream("ar", new FileInputStream(file))) {
			while ((entry = (ArArchiveEntry) debStream.getNextEntry()) != null) {
				if (!entry.getName().startsWith("control.tar.")) {
					continue;
				}
				InputStream is;
				if (entry.getName().endsWith(".gz")) {
					is = new GZIPInputStream(debStream);
				} else if (entry.getName().endsWith(".xz")) {
					is = new XZCompressorInputStream(debStream);
				} else {
					throw new ArchiveException("unsupported archive type: " + entry.getName());
				}
				try (ArchiveInputStream controlTgz = new ArchiveStreamFactory().createArchiveInputStream("tar", is)) {
					while ((controlEntry = (TarArchiveEntry) controlTgz.getNextEntry()) != null) {
						LOG.debug("control entry: {}", controlEntry.getName());
						if (!controlEntry.getName().equals("./control") && !controlEntry.getName().equals("control")) {
							continue;
						}
						ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
						IOUtils.copy(controlTgz, outputStream);
						String contentString = outputStream.toString("UTF-8");
						outputStream.close();
						ControlFile control = new ControlFile();
						control.load(contentString);
						return control;
					}
				}
			}
		}
		return null;
	}

	public ControlFile getControl() {
		return control;
	}

	public void setControl(ControlFile control) {
		this.control = control;
	}

	public File getFile() {
		return file;
	}

	public void setFile(File file) {
		this.file = file;
	}

	public FileInfo getInfo() {
		return info;
	}

	public void setInfo(FileInfo info) {
		this.info = info;
	}

}
