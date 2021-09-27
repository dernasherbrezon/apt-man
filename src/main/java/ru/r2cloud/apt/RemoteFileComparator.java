package ru.r2cloud.apt;

import java.util.Comparator;

import ru.r2cloud.apt.model.RemoteFile;

public class RemoteFileComparator implements Comparator<RemoteFile> {

	public static final RemoteFileComparator INSTANCE = new RemoteFileComparator();

	@Override
	public int compare(RemoteFile o1, RemoteFile o2) {
		return Long.compare(o1.getLastModifiedTime(), o2.getLastModifiedTime());
	}
}
