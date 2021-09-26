package ru.r2cloud.apt;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;

import org.apache.commons.compress.utils.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ru.r2cloud.apt.model.Release;
import ru.r2cloud.apt.model.SignConfiguration;

public class GpgSigner {

	private static final Logger LOG = LoggerFactory.getLogger(GpgSigner.class);

	private final SignConfiguration signConfig;
	private final Transport transport;

	public GpgSigner(SignConfiguration signConfig, Transport transport) {
		this.signConfig = signConfig;
		this.transport = transport;
	}

	public void signAndSave(String path, Release release, boolean clearsign) throws IOException {
		StringBuilder command = new StringBuilder();
		command.append(signConfig.getGpgCommand());
		command.append(" --local-user ");
		command.append(signConfig.getKeyname());
		if (clearsign) {
			command.append(" --clearsign ");
		}
		command.append(" --armor --detach-sign --batch --passphrase ");
		command.append(signConfig.getPassphrase());
		ProcessBuilder pb = new ProcessBuilder(command.toString().split(" "));
		Process proc = pb.start();
		release.save(proc.getOutputStream());
		proc.getOutputStream().close();
		StringBuilder error = new StringBuilder();
		String curLine = null;
		BufferedReader r = new BufferedReader(new InputStreamReader(proc.getErrorStream()));
		while ((curLine = r.readLine()) != null) {
			error.append(curLine).append("\n");
		}
		transport.save(path, new IOCallback() {

			@Override
			public void save(OutputStream os) throws IOException {
				IOUtils.copy(proc.getInputStream(), os);
			}

			@Override
			public void load(InputStream is) throws IOException {
				// do nothing
			}
		});
		int code;
		try {
			code = proc.waitFor();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IOException("unable to wait for process completion");
		}
		if (code != 0) {
			LOG.error("unable to sign: {}", error.toString().trim());
		}
	}

}
