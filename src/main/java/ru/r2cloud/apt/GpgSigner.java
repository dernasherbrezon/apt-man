package ru.r2cloud.apt;

import java.io.IOException;

import ru.r2cloud.apt.model.Release;

public interface GpgSigner {

	/**
	 * Sign Release file and save via provided {@link ru.r2cloud.apt.Transport}
	 * 
	 * @param path      - target path where to save signed file
	 * @param release   - {@link ru.r2cloud.apt.model.Release} structure containing
	 *                  all required info
	 * @param clearsign - Format of signed file: clearsign - keep signature and
	 *                  input Release file together or just gpg signature
	 * @throws IOException - on any error
	 */
	void signAndSave(String path, Release release, boolean clearsign, Transport transport) throws IOException;

}
