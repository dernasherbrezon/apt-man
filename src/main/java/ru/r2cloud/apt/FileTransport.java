package ru.r2cloud.apt;

import java.io.File;
import java.io.IOException;

public class FileTransport implements Transport {

	private final File basedir;

	public FileTransport(String basedirPath) throws IOException {
		this.basedir = new File(basedirPath);
		if (!this.basedir.exists() && !this.basedir.mkdirs()) {
			throw new IOException("basedir doesn't exist and cannot be created: " + basedir.getAbsolutePath());
		}
	}

	@Override
	public void save(String path, File file) {
		// TODO Auto-generated method stub

	}

	@Override
	public void save(String path, IOCallback callback) throws IOException {
		// TODO Auto-generated method stub

	}

	@Override
	public void saveGzipped(String path, IOCallback callback) {
		// TODO Auto-generated method stub

	}

	@Override
	public void load(String path, IOCallback callback) throws IOException, ResourceDoesNotExistException {
		// TODO Auto-generated method stub

	}

	@Override
	public void loadGzipped(String path, IOCallback callback) throws IOException, ResourceDoesNotExistException {
		// TODO Auto-generated method stub

	}

}
