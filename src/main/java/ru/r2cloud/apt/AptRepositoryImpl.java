package ru.r2cloud.apt;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.zip.GZIPOutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ru.r2cloud.apt.model.Architecture;
import ru.r2cloud.apt.model.ControlFile;
import ru.r2cloud.apt.model.DebFile;
import ru.r2cloud.apt.model.FileInfo;
import ru.r2cloud.apt.model.Packages;
import ru.r2cloud.apt.model.Release;

public class AptRepositoryImpl implements AptRepository {

	private static final Logger LOG = LoggerFactory.getLogger(AptRepositoryImpl.class);

	private final String component;
	private final String codename;
	private final Transport transport;
	private final GpgSigner signer;

	public AptRepositoryImpl(String codename, String component, GpgSigner signer, Transport transport) {
		this.codename = codename;
		this.component = component;
		this.transport = transport;
		this.signer = signer;
	}

	@Override
	public void saveFiles(List<DebFile> debFiles) throws IOException {
		Map<Architecture, Packages> packagesPerArch = new HashMap<>();

		for (DebFile f : debFiles) {
			ControlFile controlFile = f.getControl();
			String path = "pool/" + component + "/" + controlFile.getPackageName().charAt(0) + "/" + controlFile.getPackageName() + "/" + controlFile.getPackageName() + "_" + controlFile.getVersion() + "_" + controlFile.getArch() + ".deb";
			FileInfo fileInfo = f.getInfo();
			controlFile.append("Filename: " + path);
			controlFile.append("Size: " + fileInfo.getSize());
			controlFile.append("MD5sum: " + fileInfo.getMd5());
			controlFile.append("SHA1: " + fileInfo.getSha1());
			controlFile.append("SHA256: " + fileInfo.getSha256());
			Set<Architecture> archs = new HashSet<>();
			if (controlFile.getArch().isWildcard()) {
				for (Architecture cur : Architecture.values()) {
					if (cur.isWildcard()) {
						continue;
					}
					archs.add(cur);
				}
			} else {
				archs.add(controlFile.getArch());
			}

			for (Architecture cur : archs) {
				Packages curPackages = packagesPerArch.get(cur);
				if (curPackages == null) {
					curPackages = loadPackages(cur);
					packagesPerArch.put(cur, curPackages);
				}
				curPackages.add(controlFile);
			}

			LOG.info("uploading: {} to {}", f.getFile().getAbsolutePath(), path);
			transport.save(path, f.getFile());
		}

		Release release = loadRelease();
		// append arch and component to the existing
		release.getComponents().add(component);
		// force using by-hash
		release.setByHash(true);
		for (Architecture cur : packagesPerArch.keySet()) {
			release.getArchitectures().add(cur.name());
		}
		// retain old fileinfo
		Map<String, FileInfo> fileinfoByFilename = new HashMap<>();
		for (FileInfo cur : release.getFiles()) {
			fileinfoByFilename.put(cur.getFilename(), cur);
		}
		// add and override with new fileinfo
		for (Packages cur : packagesPerArch.values()) {
			for (FileInfo resultInfo : uploadPackages(cur)) {
				fileinfoByFilename.put(resultInfo.getFilename(), resultInfo);
			}
		}
		release.setFiles(new HashSet<>(fileinfoByFilename.values()));

		saveWithLog(getReleasePath(), release);

		if (signer != null) {
			String gpgReleasePath = getReleasePath() + ".gpg";
			LOG.info("uploading gpg release file: {}", gpgReleasePath);
			signer.signAndSave(gpgReleasePath, release, false, transport);

			String clearsignReleasePath = "dists/" + codename + "/InRelease";
			LOG.info("uploading clearsign release file: {}", clearsignReleasePath);
			signer.signAndSave(clearsignReleasePath, release, true, transport);
		}
	}

	private List<FileInfo> uploadPackages(Packages packages) throws IOException {
		List<FileInfo> result = new ArrayList<>();

		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		packages.save(baos);
		byte[] data = baos.toByteArray();
		FileInfo fileInfo = new FileInfo();
		fileInfo.setSize(String.valueOf(data.length));
		fileInfo.load(new ByteArrayInputStream(data));
		fileInfo.setFilename(getPackagesBasePath(packages.getArchitecture()));
		result.add(fileInfo);

		saveWithLog(getPackagesPath(packages.getArchitecture()), packages);
		saveWithLog(getPackagesPathParent(packages.getArchitecture()) + "/by-hash/MD5Sum/" + fileInfo.getMd5(), packages);
		saveWithLog(getPackagesPathParent(packages.getArchitecture()) + "/by-hash/SHA1/" + fileInfo.getSha1(), packages);
		saveWithLog(getPackagesPathParent(packages.getArchitecture()) + "/by-hash/SHA256/" + fileInfo.getSha256(), packages);

		// gzipped
		baos = new ByteArrayOutputStream();
		try (OutputStream os = new GZIPOutputStream(baos)) {
			packages.save(os);
		}
		data = baos.toByteArray();
		fileInfo = new FileInfo();
		fileInfo.setSize(String.valueOf(data.length));
		fileInfo.load(new ByteArrayInputStream(data));
		fileInfo.setFilename(getPackagesBasePath(packages.getArchitecture()) + ".gz");
		result.add(fileInfo);

		saveGzippedWithLog(getPackagesPath(packages.getArchitecture()) + ".gz", packages);
		saveGzippedWithLog(getPackagesPathParent(packages.getArchitecture()) + "/by-hash/MD5Sum/" + fileInfo.getMd5(), packages);
		saveGzippedWithLog(getPackagesPathParent(packages.getArchitecture()) + "/by-hash/SHA1/" + fileInfo.getSha1(), packages);
		saveGzippedWithLog(getPackagesPathParent(packages.getArchitecture()) + "/by-hash/SHA256/" + fileInfo.getSha256(), packages);

		return result;
	}

	private Release loadRelease() throws IOException {
		Release result = new Release();
		try {
			transport.load(getReleasePath(), result);
		} catch (ResourceDoesNotExistException e) {
			LOG.info("create missing file: {}", getReleasePath());
			result.setCodename(codename);
			result.setLabel(codename);
			result.setOrigin(codename);
		}
		SimpleDateFormat sdf = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz");
		sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
		result.setDate(sdf.format(new Date()));
		return result;
	}

	private Packages loadPackages(Architecture arch) {
		String path = getPackagesPath(arch) + ".gz";
		try {
			Packages result = new Packages();
			transport.loadGzipped(path, result);
			result.setArchitecture(arch);
			return result;
		} catch (Exception e) {
			LOG.info("create missing file: {}", path);
			Packages newPackages = new Packages();
			newPackages.setArchitecture(arch);
			return newPackages;
		}
	}

	@Override
	public void saveFile(DebFile debFile) throws IOException {
		saveFiles(Collections.singletonList(debFile));
	}

	private String getPackagesBasePath(Architecture architecture) {
		return component + "/binary-" + architecture.name() + "/Packages";
	}

	private String getPackagesPath(Architecture architecture) {
		return "dists/" + codename + "/" + getPackagesBasePath(architecture);
	}

	private String getPackagesPathParent(Architecture architecture) {
		return "dists/" + codename + "/" + component + "/binary-" + architecture.name();
	}

	private String getReleasePath() {
		return "dists/" + codename + "/Release";
	}

	private void saveWithLog(String path, IOCallback callback) throws IOException {
		LOG.info("uploading: {}", path);
		transport.save(path, callback);
	}

	private void saveGzippedWithLog(String path, IOCallback callback) throws IOException {
		LOG.info("uploading: {}", path);
		transport.saveGzipped(path, callback);
	}
}
