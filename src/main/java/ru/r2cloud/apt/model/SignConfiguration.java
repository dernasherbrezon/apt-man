package ru.r2cloud.apt.model;

import java.util.List;

public class SignConfiguration {

	private String gpgCommand;
	private String keyname;
	private String passphrase;
	private List<String> gpgArguments;
	private String secretKeyFilename;
	private String hashAlgorithm;
	
	public String getSecretKeyFilename() {
		return secretKeyFilename;
	}
	
	public void setSecretKeyFilename(String secretKeyFilename) {
		this.secretKeyFilename = secretKeyFilename;
	}
	
	public String getHashAlgorithm() {
		return hashAlgorithm;
	}
	
	public void setHashAlgorithm(String hashAlgorithm) {
		this.hashAlgorithm = hashAlgorithm;
	}
	
	public List<String> getGpgArguments() {
		return gpgArguments;
	}
	
	public void setGpgArguments(List<String> gpgArguments) {
		this.gpgArguments = gpgArguments;
	}

	public String getGpgCommand() {
		return gpgCommand;
	}

	public void setGpgCommand(String gpgCommand) {
		this.gpgCommand = gpgCommand;
	}

	public String getKeyname() {
		return keyname;
	}

	public void setKeyname(String keyname) {
		this.keyname = keyname;
	}

	public String getPassphrase() {
		return passphrase;
	}

	public void setPassphrase(String passphrase) {
		this.passphrase = passphrase;
	}

}
