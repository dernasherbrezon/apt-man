package ru.r2cloud.apt;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class GzippedCallback implements IOCallback {

	private final IOCallback impl;

	public GzippedCallback(IOCallback impl) {
		this.impl = impl;
	}

	@Override
	public void load(InputStream is) throws IOException {
		impl.load(new GZIPInputStream(is));
	}

	@Override
	public void save(OutputStream os) throws IOException {
		impl.save(new GZIPOutputStream(os));
	}
}
