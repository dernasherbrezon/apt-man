package ru.r2cloud.apt;

import java.io.File;
import java.io.IOException;

public interface Transport {

	void save(String path, File file);

	void save(String path, IOCallback callback) throws IOException;

	void saveGzipped(String path, IOCallback callback);

	void load(String path, IOCallback callback) throws IOException, ResourceDoesNotExistException;

	void loadGzipped(String path, IOCallback callback) throws IOException, ResourceDoesNotExistException;

}
