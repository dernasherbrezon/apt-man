package ru.r2cloud.apt;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.apache.commons.compress.utils.IOUtils;

public class FileTransport implements Transport {

	private final File basedir;

	public FileTransport(String basedirPath) throws IOException {
		this.basedir = new File(basedirPath);
		if (!this.basedir.exists() && !this.basedir.mkdirs()) {
			throw new IOException("basedir doesn't exist and cannot be created: " + basedir.getAbsolutePath());
		}
	}

	@Override
	public void save(String path, File file) throws IOException {
		File targetFile = new File(basedir, path);
		try (OutputStream os = new BufferedOutputStream(new FileOutputStream(targetFile)); InputStream is = new BufferedInputStream(new FileInputStream(file))) {
			IOUtils.copy(is, os);
		}
	}

	@Override
	public void save(String path, IOCallback callback) throws IOException {
		File file = new File(basedir, path);
		try (OutputStream os = new BufferedOutputStream(new FileOutputStream(file))) {
			callback.save(os);
		}
	}

	@Override
	public void load(String path, IOCallback callback) throws IOException, ResourceDoesNotExistException {
		File file = new File(basedir, path);
		if (!file.exists()) {
			throw new ResourceDoesNotExistException();
		}
		try (InputStream is = new BufferedInputStream(new FileInputStream(file))) {
			callback.load(is);
		}
	}

	@Override
	public void saveGzipped(String path, IOCallback callback) throws IOException {
		save(path, new GzippedCallback(callback));
	}

	@Override
	public void loadGzipped(String path, IOCallback callback) throws IOException, ResourceDoesNotExistException {
		load(path, new GzippedCallback(callback));
	}

}
