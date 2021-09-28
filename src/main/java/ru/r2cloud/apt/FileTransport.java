package ru.r2cloud.apt;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.apache.commons.compress.utils.IOUtils;

import ru.r2cloud.apt.model.RemoteFile;

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
		setupParentDir(targetFile);
		try (OutputStream os = new BufferedOutputStream(new FileOutputStream(targetFile)); InputStream is = new BufferedInputStream(new FileInputStream(file))) {
			IOUtils.copy(is, os);
		}
	}

	@Override
	public void save(String path, IOCallback callback) throws IOException {
		File file = new File(basedir, path);
		setupParentDir(file);
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
		File file = new File(basedir, path);
		setupParentDir(file);
		try (OutputStream os = new BufferedOutputStream(new GZIPOutputStream(new FileOutputStream(file)))) {
			callback.save(os);
		}
	}

	@Override
	public void loadGzipped(String path, IOCallback callback) throws IOException, ResourceDoesNotExistException {
		File file = new File(basedir, path);
		if (!file.exists()) {
			throw new ResourceDoesNotExistException();
		}
		try (InputStream is = new BufferedInputStream(new GZIPInputStream(new FileInputStream(file)))) {
			callback.load(is);
		}
	}

	@Override
	public List<RemoteFile> listFiles(String path) {
		if (!path.endsWith("/")) {
			path += "/";
		}
		File dir = new File(basedir, path);
		if (!dir.exists() || !dir.isDirectory()) {
			return Collections.emptyList();
		}
		File[] files = dir.listFiles();
		List<RemoteFile> result = new ArrayList<>();
		for (File cur : files) {
			RemoteFile curRemoteFile = new RemoteFile();
			// always use "/" because paths are based on "/"
			curRemoteFile.setPath(path + cur.getName());
			curRemoteFile.setLastModifiedTime(cur.lastModified());
			curRemoteFile.setDirectory(cur.isDirectory());
			result.add(curRemoteFile);
		}
		return result;
	}

	@Override
	public void delete(String path) throws IOException {
		File fileToDelete = new File(basedir, path);
		if (!fileToDelete.exists()) {
			return;
		}
		if (!fileToDelete.delete()) {
			throw new IOException("unable to delete: " + fileToDelete.getAbsolutePath());
		}
	}

	private static void setupParentDir(File targetFile) throws IOException {
		File parent = targetFile.getParentFile();
		if (!parent.exists() && !parent.mkdirs()) {
			throw new IOException("cannot create parent dir");
		}
	}
}
