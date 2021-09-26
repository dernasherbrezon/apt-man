package ru.r2cloud.apt;

import static org.junit.Assert.assertNotNull;

import org.junit.Test;

import ru.r2cloud.apt.model.Packages;

public class PackagesTest {

	@Test
	public void load() throws Exception {
		Packages packages = new Packages();
		packages.load(PackagesTest.class.getClassLoader().getResourceAsStream("Packages"));
		assertNotNull(packages.getContents().get("r2cloud-ui"));
	}
	
}
