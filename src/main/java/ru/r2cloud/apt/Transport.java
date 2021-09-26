package ru.r2cloud.apt;

import java.io.File;
import java.io.IOException;

/**
 * Interface for providing different protocols for working with apt
 * repositories. Some of these protocols might include:
 * <ul>
 * <li>File</li>
 * <li>Http</li>
 * <li>Amazon S3</li>
 * <li>Maven's Wagon</li>
 * </ul>
 * 
 * @author dernasherbrezon
 *
 */
public interface Transport {

	void save(String path, File file) throws IOException;

	void save(String path, IOCallback callback) throws IOException;

	void saveGzipped(String path, IOCallback callback) throws IOException;

	void load(String path, IOCallback callback) throws IOException, ResourceDoesNotExistException;

	void loadGzipped(String path, IOCallback callback) throws IOException, ResourceDoesNotExistException;

}
