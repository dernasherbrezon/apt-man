package ru.r2cloud.apt.model;

public enum Architecture {

	AMD64(false), I386(false), ANY(true), ALL(true), ARMHF(false);
	
	private final boolean wildcard;
	
	private Architecture(boolean wildcard) {
		this.wildcard = wildcard;
	}
	
	public boolean isWildcard() {
		return wildcard;
	}

}
