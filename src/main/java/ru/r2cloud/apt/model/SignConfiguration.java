package ru.r2cloud.apt.model;

public class SignConfiguration {

	private String gpgCommand;
	private String keyname;
	private String passphrase;

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
