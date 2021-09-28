[![Build Status](https://app.travis-ci.com/dernasherbrezon/apt-man.svg?branch=main)](https://app.travis-ci.com/github/dernasherbrezon/apt-man) [![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=ru.r2cloud%3Aapt-man&metric=alert_status)](https://sonarcloud.io/dashboard?id=ru.r2cloud%3Aapt-man)

# About

Library for managing APT repository. This library is lightweight and can be embedded into different applications.

# Features

  * Save .deb files. Simply upload multiple .deb files into APT repository.
  * Cleanup repository. Delete unused files from APT repository.
  * Delete packages. Some packages can be no longer needed into the repository or moved somewhere else.
  
# Extending

apt-man supports several extension points:

 * ru.r2cloud.apt.Transport. This interface is for accessing local or remote APT repositories. By default only FileTransport is available.
 * ru.r2cloud.apt.GpgSigner. This interface is for signing files in APT repositories. Default ru.r2cloud.apt.GpgSignerImpl uses system "gpg" command to sign.
 
# Example

```java
SignConfiguration config = new SignConfiguration();
config.setGpgCommand("gpg");
config.setKeyname("123");
config.setPassphrase("123");
GpgSigner signer = new GpgSignerImpl(config);
AptRepository repo = new AptRepositoryImpl("stretch", "main", signer, new FileTransport("/var/www/apt"));
repo.saveFiles(Collections.singletonList(new DebFile(new File("rtl-sdr_0.6_armhf.deb"))));
repo.deletePackages(Collections.singleton("rtl-sdr"));
repo.cleanup(1);
```