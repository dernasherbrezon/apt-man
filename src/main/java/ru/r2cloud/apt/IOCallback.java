package ru.r2cloud.apt;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public interface IOCallback {

	void load(InputStream is) throws IOException;
	
	void save(OutputStream os) throws IOException;

}
