package ru.r2cloud.apt.model;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.HashMap;
import java.util.Map;

import ru.r2cloud.apt.IOCallback;

public class Packages implements IOCallback {

	private Architecture architecture;
	private final Map<String, ControlFile> contents = new HashMap<>();

	@Override
	public void load(InputStream is) throws IOException {
		String curLine = null;
		BufferedReader r = new BufferedReader(new InputStreamReader(is, "UTF-8"));
		StringBuilder currentControl = new StringBuilder();
		while (true) {
			curLine = r.readLine();
			if (curLine == null || curLine.trim().length() == 0) {
				String currentControlStr = currentControl.toString();
				currentControl = new StringBuilder();
				if (currentControlStr.trim().length() != 0) {
					ControlFile curFile = new ControlFile();
					curFile.load(currentControlStr);
					contents.put(curFile.getPackageName(), curFile);
				}
				if (curLine == null) {
					break;
				}
				continue;
			}
			currentControl.append(curLine).append("\n");
		}
	}

	@Override
	public void save(OutputStream os) throws IOException {
		BufferedWriter w = new BufferedWriter(new OutputStreamWriter(os, "UTF-8"));
		for (ControlFile cur : contents.values()) {
			w.append(cur.getContents()).append("\n");
		}
		w.flush();
	}

	public void add(ControlFile file) {
		contents.put(file.getPackageName(), file);
	}

	public void setArchitecture(Architecture architecture) {
		this.architecture = architecture;
	}

	public Architecture getArchitecture() {
		return architecture;
	}

	// used only in tests
	Map<String, ControlFile> getContents() {
		return contents;
	}

}
