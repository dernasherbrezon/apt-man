package ru.r2cloud.apt.model;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.apache.commons.codec.binary.Hex;

public class FileInfo {

	private String md5;
	private String sha1;
	private String sha256;
	private String size;
	private String filename;

	public void load(InputStream is) throws IOException {
		BufferedInputStream bis = new BufferedInputStream(is);
		try {
			MessageDigest md5Alg = MessageDigest.getInstance("MD5");
			md5Alg.reset();
			MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
			sha1.reset();
			MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
			sha256.reset();
			byte[] buf = new byte[2048];
			int curByte = -1;
			while ((curByte = bis.read(buf)) != -1) {
				md5Alg.update(buf, 0, curByte);
				sha1.update(buf, 0, curByte);
				sha256.update(buf, 0, curByte);
			}
			setMd5(new String(Hex.encodeHex(md5Alg.digest())));
			setSha1(new String(Hex.encodeHex(sha1.digest())));
			setSha256(new String(Hex.encodeHex(sha256.digest())));
		} catch (NoSuchAlgorithmException e) {
			throw new IOException("unsupported algorithm", e);
		}
	}

	public String getMd5() {
		return md5;
	}

	public void setMd5(String md5) {
		this.md5 = md5;
	}

	public String getSha1() {
		return sha1;
	}

	public void setSha1(String sha1) {
		this.sha1 = sha1;
	}

	public String getSha256() {
		return sha256;
	}

	public void setSha256(String sha256) {
		this.sha256 = sha256;
	}

	public String getSize() {
		return size;
	}

	public void setSize(String size) {
		this.size = size;
	}

	public String getFilename() {
		return filename;
	}

	public void setFilename(String filename) {
		this.filename = filename;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((filename == null) ? 0 : filename.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		FileInfo other = (FileInfo) obj;
		if (filename == null) {
			if (other.filename != null)
				return false;
		} else if (!filename.equals(other.filename))
			return false;
		return true;
	}

}
