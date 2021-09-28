package ru.r2cloud.apt;

public class ResourceDoesNotExistException extends Exception {

	private static final long serialVersionUID = -8997629339000400780L;

	public ResourceDoesNotExistException() {
		// do nothing
	}

	public ResourceDoesNotExistException(Throwable e) {
		super(e);
	}

	public ResourceDoesNotExistException(String message, Throwable e) {
		super(message, e);
	}

}
