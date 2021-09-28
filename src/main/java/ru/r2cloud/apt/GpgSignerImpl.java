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

/**
 * Default GpgSigner based on system's "gpg" program
 * 
 * @author dernasherbrezon
 *
 */
public class GpgSignerImpl implements GpgSigner {

	private static final Logger LOG = LoggerFactory.getLogger(GpgSignerImpl.class);

	private final SignConfiguration signConfig;

	public GpgSignerImpl(SignConfiguration signConfig) {
		this.signConfig = signConfig;
	}

	@Override
	public void signAndSave(String path, Release release, boolean clearsign, Transport transport) throws IOException {
		StringBuilder command = new StringBuilder();
		command.append(signConfig.getGpgCommand());
		command.append(" --local-user ");
		command.append(signConfig.getKeyname());
		command.append(" --armor --detach-sign --batch --passphrase ");
		command.append(signConfig.getPassphrase());
		if (clearsign) {
			command.append(" --clearsign ");
		}
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
		if (code != 0 && LOG.isErrorEnabled()) {
			LOG.error("unable to sign: {}", error.toString().trim());
		}
	}

}
