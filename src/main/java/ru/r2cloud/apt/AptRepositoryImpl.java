package ru.r2cloud.apt;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
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
import ru.r2cloud.apt.model.RemoteFile;
import ru.r2cloud.apt.model.ValidationError;
import ru.r2cloud.apt.model.ValidationErrorCode;

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
		if (debFiles.isEmpty()) {
			LOG.info("no files to save. skipping...");
			return;
		}
		Map<Architecture, Packages> packagesPerArch = new EnumMap<>(Architecture.class);

		for (DebFile f : debFiles) {
			ControlFile controlFile = f.getControl();
			String path = "pool/" + component + "/" + controlFile.getPackageName().charAt(0) + "/" + controlFile.getPackageName() + "/" + controlFile.getPackageName() + "_" + controlFile.getVersion() + "_" + controlFile.getArch().name().toLowerCase(Locale.UK) + ".deb";
			FileInfo fileInfo = f.getInfo();
			controlFile.append("Filename: " + path);
			controlFile.append("Size: " + fileInfo.getSize());
			controlFile.append("MD5sum: " + fileInfo.getMd5());
			controlFile.append("SHA1: " + fileInfo.getSha1());
			controlFile.append("SHA256: " + fileInfo.getSha256());
			Set<Architecture> archs = readArchs(controlFile);

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
		// add new architectures if any
		for (Architecture cur : packagesPerArch.keySet()) {
			release.getArchitectures().add(cur.name().toLowerCase(Locale.UK));
		}

		reindex(release, packagesPerArch.values());
	}

	@Override
	public void init(Architecture... architectures) throws IOException {
		if (architectures == null || architectures.length == 0) {
			LOG.info("no architectures provided. skipping...");
			return;
		}
		Release release = loadRelease();
		// append arch and component to the existing
		release.getComponents().add(component);

		List<Packages> emptyPackages = new ArrayList<>(architectures.length);
		for (Architecture cur : architectures) {
			Packages oldPackages = loadPackagesOrNull(cur);
			if (oldPackages != null && !oldPackages.getContents().isEmpty()) {
				throw new IOException("repository is not empty: " + cur + " number of packages: " + oldPackages.getContents().size());
			}
			release.getArchitectures().add(cur.name().toLowerCase(Locale.UK));

			Packages curPackages = new Packages();
			curPackages.setArchitecture(cur);
			emptyPackages.add(curPackages);
		}

		reindex(release, emptyPackages);
	}

	private void reindex(Release release, Collection<Packages> packages) throws IOException {
		// retain old fileinfo
		Map<String, FileInfo> fileinfoByFilename = new HashMap<>();
		for (FileInfo cur : release.getFiles()) {
			fileinfoByFilename.put(cur.getFilename(), cur);
		}
		// add and override with new fileinfo
		for (Packages cur : packages) {
			for (FileInfo resultInfo : uploadPackages(cur)) {
				fileinfoByFilename.put(resultInfo.getFilename(), resultInfo);
			}
		}
		release.setFiles(new HashSet<>(fileinfoByFilename.values()));
		reindex(release);
	}

	private void reindex(Release release) throws IOException {
		String clearsignReleasePath = "dists/" + codename + "/InRelease";
		if (signer == null) {
			try {
				long size = transport.getFileSize(clearsignReleasePath);
				// getFileSize also checks if file exist
				if (size != 0) {
					LOG.error("repository is gpg signed, but no signing information provided");
					return;
				}
			} catch (ResourceDoesNotExistException e) {
				// do nothing
			}
		}

		// force using by-hash
		release.setByHash(true);

		saveWithLog(getReleasePath(), release);

		if (signer != null) {
			String gpgReleasePath = getReleasePath() + ".gpg";
			LOG.info("uploading gpg release file: {}", gpgReleasePath);
			signer.signAndSave(gpgReleasePath, release, false, transport);

			LOG.info("uploading clearsign release file: {}", clearsignReleasePath);
			signer.signAndSave(clearsignReleasePath, release, true, transport);
		}
	}

	@Override
	public void cleanup(int keepLast) throws IOException {
		Release release = loadRelease();
		for (String arch : release.getArchitectures()) {
			Architecture curArch = Architecture.valueOf(arch.toUpperCase(Locale.UK));

			if (release.isByHash()) {
				FileInfo info = findPackageInfo(getPackagesPath(curArch), release);
				FileInfo gzippedInfo = findPackageInfo(getPackagesPath(curArch) + ".gz", release);

				String byHashPrefix = "dists/" + codename + "/" + component + "/binary-" + arch + "/by-hash";
				Set<String> ignore = new HashSet<>();
				if (info != null) {
					ignore.add(byHashPrefix + "/MD5Sum/" + info.getMd5());
					ignore.add(byHashPrefix + "/SHA1/" + info.getSha1());
					ignore.add(byHashPrefix + "/SHA256/" + info.getSha256());
				}
				if (gzippedInfo != null) {
					ignore.add(byHashPrefix + "/MD5Sum/" + gzippedInfo.getMd5());
					ignore.add(byHashPrefix + "/SHA1/" + gzippedInfo.getSha1());
					ignore.add(byHashPrefix + "/SHA256/" + gzippedInfo.getSha256());
				}
				// keep times 2 hashes because /by-hash/*/ might contain both gzipped and
				// plain hashes
				int hashesToKeep = (keepLast - 1) * 2;
				cleanup(hashesToKeep, filterByName(transport.listFiles(byHashPrefix + "/MD5Sum/"), ignore));
				cleanup(hashesToKeep, filterByName(transport.listFiles(byHashPrefix + "/SHA1/"), ignore));
				cleanup(hashesToKeep, filterByName(transport.listFiles(byHashPrefix + "/SHA256/"), ignore));
			}

			Packages packages = loadPackages(curArch);

			for (ControlFile control : packages.getContents().values()) {
				String packageBaseDir = extractParentPath(control.getFilename());
				// filename might be corrupted
				if (packageBaseDir == null) {
					continue;
				}
				// make sure active package file was not deleted
				cleanup(keepLast - 1, filterByName(transport.listFiles(packageBaseDir), Collections.singleton(control.getFilename())));
			}
		}
	}

	@Override
	public void deletePackages(Set<String> packages) throws IOException {
		if (packages.isEmpty()) {
			LOG.info("no packages to delete. skipping...");
			return;
		}
		Release release = loadRelease();

		List<Packages> toUpdate = new ArrayList<>();
		Set<String> allBasepathsToDelete = new HashSet<>();
		for (String arch : release.getArchitectures()) {
			Architecture curArch = Architecture.valueOf(arch.toUpperCase(Locale.UK));

			Packages packagesFile = loadPackages(curArch);
			Set<String> basepathsToDelete = new HashSet<>();
			for (String cur : packages) {
				ControlFile control = packagesFile.getContents().remove(cur);
				if (control == null) {
					continue;
				}
				basepathsToDelete.add(extractParentPath(control.getFilename()));
			}

			// no such package found. do not re-upload packages
			if (basepathsToDelete.isEmpty()) {
				continue;
			}

			allBasepathsToDelete.addAll(basepathsToDelete);
			toUpdate.add(packagesFile);
		}

		if (toUpdate.isEmpty()) {
			return;
		}

		reindex(release, toUpdate);
		for (String curPath : allBasepathsToDelete) {
			List<RemoteFile> files = transport.listFiles(curPath);
			for (RemoteFile curFile : files) {
				if (curFile.isDirectory()) {
					continue;
				}
				LOG.info("deleting: {}", curFile.getPath());
				transport.delete(curFile.getPath());
			}
			LOG.info("deleting: {}", curPath);
			transport.delete(curPath);
		}
	}

	@Override
	public void deleteArchitectures(Architecture... architectures) throws IOException {
		if (architectures == null || architectures.length == 0) {
			LOG.info("no architectures provided. skipping...");
			return;
		}
		Release release = new Release();
		try {
			transport.load(getReleasePath(), release);
		} catch (ResourceDoesNotExistException e) {
			LOG.info("can't find release");
			return;
		}

		Set<String> toDelete = new HashSet<>();
		for (Architecture cur : architectures) {
			String packagePath = getPackagesPath(cur);
			String packagePathGz = getPackagesPath(cur) + ".gz";
			FileInfo info = null;
			FileInfo gzippedInfo = null;
			Iterator<FileInfo> it = release.getFiles().iterator();
			while (it.hasNext()) {
				FileInfo curFile = it.next();
				String curFilename = "dists/" + release.getCodename() + "/" + curFile.getFilename();
				if (curFilename.equalsIgnoreCase(packagePath)) {
					info = curFile;
					it.remove();
				} else if (curFilename.equalsIgnoreCase(packagePathGz)) {
					gzippedInfo = curFile;
					it.remove();
				}
			}

			markForDeletion(toDelete, packagePath, info, cur, release);
			markForDeletion(toDelete, packagePathGz, gzippedInfo, cur, release);
			release.getArchitectures().remove(cur.toString().toLowerCase(Locale.UK));
		}

		if (toDelete.isEmpty()) {
			LOG.info("can't find files to delete. looks good");
			return;
		}
		reindex(release);

		for (String cur : toDelete) {
			LOG.info("deleting: {}", cur);
			transport.delete(cur);
		}
	}

	private void markForDeletion(Set<String> toDelete, String path, FileInfo info, Architecture arch, Release release) throws IOException {
		if (info == null) {
			return;
		}
		toDelete.add(path);
		if (release.isByHash()) {
			String byHashPrefix = "dists/" + codename + "/" + component + "/binary-" + arch.toString().toLowerCase(Locale.UK) + "/by-hash";
			toDelete.add(byHashPrefix + "/MD5Sum/" + info.getMd5());
			toDelete.add(byHashPrefix + "/SHA1/" + info.getSha1());
			toDelete.add(byHashPrefix + "/SHA256/" + info.getSha256());
		}

		Packages packages = new Packages();
		try {
			if (info.getFilename().endsWith("gz")) {
				transport.loadGzipped(path, packages);
			} else {
				transport.load(path, packages);
			}
		} catch (ResourceDoesNotExistException e) {
			return;
		}

		for (ControlFile cur : packages.getContents().values()) {
			toDelete.add(cur.getFilename());
		}
	}

	@Override
	public void sign() throws IOException {
		if (signer == null) {
			throw new IOException("cannot sign repository when no GPG configuration provided");
		}

		Release release = new Release();
		try {
			transport.load(getReleasePath(), release);
		} catch (ResourceDoesNotExistException e) {
			LOG.error("cannot sign non-existing repository");
			return;
		}

		String gpgReleasePath = getReleasePath() + ".gpg";
		LOG.info("uploading gpg release file: {}", gpgReleasePath);
		signer.signAndSave(gpgReleasePath, release, false, transport);

		String clearsignReleasePath = "dists/" + codename + "/InRelease";
		LOG.info("uploading clearsign release file: {}", clearsignReleasePath);
		signer.signAndSave(clearsignReleasePath, release, true, transport);
	}

	@Override
	public List<ValidationError> validate() {
		Release release = new Release();
		try {
			transport.load(getReleasePath(), release);
		} catch (ResourceDoesNotExistException e) {
			return Collections.singletonList(new ValidationError(ValidationErrorCode.RESOURCE_IS_MISSING, "repository is missing: " + codename));
		} catch (IOException e) {
			return Collections.singletonList(new ValidationError(ValidationErrorCode.COMMUNICATION_FAILURE, "cannot connect to " + codename));
		}
		Set<ValidationError> result = new HashSet<>();
		for (String arch : release.getArchitectures()) {
			Architecture curArch = Architecture.valueOf(arch.toUpperCase(Locale.UK));
			validate(result, getPackagesPath(curArch), curArch, release);
			validate(result, getPackagesPath(curArch) + ".gz", curArch, release);
		}

		String clearsignReleasePath = "dists/" + codename + "/InRelease";
		String gpgReleasePath = getReleasePath() + ".gpg";

		if (signer == null) {
			validateNoGpgSinature(result, gpgReleasePath);
			validateNoGpgSinature(result, clearsignReleasePath);
		} else {
			validateGpgSignature(result, gpgReleasePath, release, false);
			validateGpgSignature(result, clearsignReleasePath, release, true);
		}

		List<ValidationError> sorted = new ArrayList<>(result);
		Collections.sort(sorted, new Comparator<ValidationError>() {
			@Override
			public int compare(ValidationError o1, ValidationError o2) {
				return o1.getMessage().compareTo(o2.getMessage());
			}
		});
		return sorted;
	}

	private void validateGpgSignature(Set<ValidationError> result, String path, Release release, boolean clearsign) {
		try {
			if (!signer.validate(path, release, clearsign, transport)) {
				result.add(new ValidationError(ValidationErrorCode.GPG_SIGNATURE_FAILURE, "invalid gpg signature: " + path));
			}
		} catch (IOException e) {
			result.add(new ValidationError(ValidationErrorCode.COMMUNICATION_FAILURE, "unable to read signature " + path));
		} catch (ResourceDoesNotExistException e) {
			// signing information provided, but repo doesn't have signature. i.e. unsigned
			// repo. it's ok.
		}
	}

	private void validateNoGpgSinature(Set<ValidationError> result, String path) {
		try {
			long size = transport.getFileSize(path);
			// getFileSize also checks if file exist
			if (size != 0) {
				result.add(new ValidationError(ValidationErrorCode.GPG_SIGNATURE_FAILURE, "repository contains gpg signature, but no gpg information provided. unable to verify"));
			}
		} catch (ResourceDoesNotExistException e) {
			// do nothing
		} catch (IOException e) {
			result.add(new ValidationError(ValidationErrorCode.COMMUNICATION_FAILURE, "unable to read signature " + path));
		}
	}

	private void validate(Set<ValidationError> errors, String path, Architecture arch, Release release) {
		FileInfo info = findPackageInfo(path, release);
		if (info == null) {
			errors.add(new ValidationError(ValidationErrorCode.RESOURCE_IS_MISSING, "can't find: " + path));
			return;
		}
		if (release.isByHash()) {
			String byHashPrefix = "dists/" + codename + "/" + component + "/binary-" + arch.toString().toLowerCase(Locale.UK) + "/by-hash";
			validate(errors, byHashPrefix + "/MD5Sum/" + info.getMd5(), info.getSize());
			validate(errors, byHashPrefix + "/SHA1/" + info.getSha1(), info.getSize());
			validate(errors, byHashPrefix + "/SHA256/" + info.getSha256(), info.getSize());
		}
		if (!validate(errors, path, info.getSize())) {
			return;
		}
		Packages packages = new Packages();
		try {
			if (info.getFilename().endsWith("gz")) {
				transport.loadGzipped(path, packages);
			} else {
				transport.load(path, packages);
			}
			packages.setArchitecture(arch);
		} catch (ResourceDoesNotExistException e) {
			errors.add(new ValidationError(ValidationErrorCode.RESOURCE_IS_MISSING, "can't find: " + info.getFilename()));
			return;
		} catch (IOException e) {
			errors.add(new ValidationError(ValidationErrorCode.COMMUNICATION_FAILURE, "cannot read " + info.getFilename()));
			return;
		}

		for (ControlFile cur : packages.getContents().values()) {
			validate(errors, cur.getFilename(), cur.getSize());
		}

	}

	private boolean validate(Set<ValidationError> errors, String path, long expectedSize) {
		try {
			long fileSize = transport.getFileSize(path);
			if (fileSize != expectedSize) {
				errors.add(new ValidationError(ValidationErrorCode.MISMATCHED_FILE_SIZE, "mismatched file size: " + path + " actual: " + fileSize + " expected: " + expectedSize));
				return false;
			}
			return true;
		} catch (IOException e) {
			errors.add(new ValidationError(ValidationErrorCode.COMMUNICATION_FAILURE, "cannot read file size " + path));
			return false;
		} catch (ResourceDoesNotExistException e) {
			errors.add(new ValidationError(ValidationErrorCode.RESOURCE_IS_MISSING, "file is missing: " + path));
			return false;
		}
	}

	private static List<RemoteFile> filterByName(List<RemoteFile> allFiles, Set<String> indexedIgnore) {
		List<RemoteFile> result = new ArrayList<>();
		for (RemoteFile cur : allFiles) {
			if (indexedIgnore.contains(cur.getPath())) {
				continue;
			}
			result.add(cur);
		}
		return result;
	}

	private void cleanup(int keepLast, List<RemoteFile> listHashes) {
		if (listHashes.isEmpty()) {
			return;
		}
		Collections.sort(listHashes, RemoteFileComparator.INSTANCE);
		for (int i = 0; i < listHashes.size() - keepLast; i++) {
			RemoteFile curFile = listHashes.get(i);
			// cannot delete directory without cleaning up everything inside of it
			if (curFile.isDirectory()) {
				continue;
			}
			String pathToDelete = curFile.getPath();
			LOG.info("deleting: {}", pathToDelete);
			try {
				transport.delete(pathToDelete);
			} catch (IOException e) {
				LOG.error("unable to delete: {}", pathToDelete, e);
			}
		}
	}

	private static Set<Architecture> readArchs(ControlFile controlFile) {
		Set<Architecture> result = new HashSet<>();
		if (controlFile.getArch().isWildcard()) {
			for (Architecture cur : Architecture.values()) {
				if (cur.isWildcard()) {
					continue;
				}
				result.add(cur);
			}
		} else {
			result.add(controlFile.getArch());
		}
		return result;
	}

	private static String extractParentPath(String filename) {
		int index = filename.lastIndexOf('/');
		if (index == -1) {
			return null;
		}
		return filename.substring(0, index);
	}

	private static FileInfo findPackageInfo(String filename, Release release) {
		for (FileInfo cur : release.getFiles()) {
			if (("dists/" + release.getCodename() + "/" + cur.getFilename()).equalsIgnoreCase(filename)) {
				return cur;
			}
		}
		return null;
	}

	private List<FileInfo> uploadPackages(Packages packages) throws IOException {
		List<FileInfo> result = new ArrayList<>();

		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		packages.save(baos);
		byte[] data = baos.toByteArray();
		FileInfo fileInfo = new FileInfo();
		fileInfo.setSize(data.length);
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
		fileInfo.setSize(data.length);
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
			result.setCodename(codename);
			result.setLabel(codename);
			result.setOrigin(codename);
		}
		SimpleDateFormat sdf = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz");
		sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
		result.setDate(sdf.format(new Date()));
		return result;
	}

	private Packages loadPackagesOrNull(Architecture arch) throws IOException {
		String path = getPackagesPath(arch) + ".gz";
		try {
			Packages result = new Packages();
			transport.loadGzipped(path, result);
			result.setArchitecture(arch);
			return result;
		} catch (ResourceDoesNotExistException e) {
			return null;
		}
	}

	private Packages loadPackages(Architecture arch) {
		String path = getPackagesPath(arch) + ".gz";
		try {
			Packages result = new Packages();
			transport.loadGzipped(path, result);
			result.setArchitecture(arch);
			return result;
		} catch (Exception e) {
			Packages newPackages = new Packages();
			newPackages.setArchitecture(arch);
			return newPackages;
		}
	}

	private String getPackagesBasePath(Architecture architecture) {
		return component + "/binary-" + architecture.name().toLowerCase(Locale.UK) + "/Packages";
	}

	private String getPackagesPath(Architecture architecture) {
		return "dists/" + codename + "/" + getPackagesBasePath(architecture);
	}

	private String getPackagesPathParent(Architecture architecture) {
		return "dists/" + codename + "/" + component + "/binary-" + architecture.name().toLowerCase(Locale.UK);
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
