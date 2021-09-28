package ru.r2cloud.apt.model;

public class RemoteFile {

	private String path;
	private long lastModifiedTime;
	private boolean directory;

	public boolean isDirectory() {
		return directory;
	}

	public void setDirectory(boolean directory) {
		this.directory = directory;
	}

	public String getPath() {
		return path;
	}

	public void setPath(String path) {
		this.path = path;
	}

	public long getLastModifiedTime() {
		return lastModifiedTime;
	}

	public void setLastModifiedTime(long lastModifiedTime) {
		this.lastModifiedTime = lastModifiedTime;
	}

}
