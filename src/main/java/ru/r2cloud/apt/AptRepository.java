package ru.r2cloud.apt;

import java.io.IOException;
import java.util.List;

import ru.r2cloud.apt.model.DebFile;

public interface AptRepository {

	void saveFiles(List<DebFile> debFiles) throws IOException;

	void saveFile(DebFile debFile) throws IOException;

}
