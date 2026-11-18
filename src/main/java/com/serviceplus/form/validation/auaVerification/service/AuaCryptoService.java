package com.serviceplus.form.validation.auaVerification.service;

import com.serviceplus.form.validation.auaVerification.dto.AuaApiConfigurationDTO;
import com.serviceplus.form.validation.auaVerification.model.AuaCryptoContext;
import com.serviceplus.form.validation.auaVerification.model.SessionKeyDetails;
import com.serviceplus.form.validation.auaVerification.model.SynchronizedKey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

import static java.util.Objects.isNull;

@Service
public class AuaCryptoService {

    private static final Logger log = LoggerFactory.getLogger(AuaCryptoService.class);

    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private static final DateTimeFormatter UIDAI_TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    @Value("${uidai.publicKeyFile}")
    private Resource publicKeyResource;

    public String createSkey(AuaCryptoContext context, AuaApiConfigurationDTO api) {

        System.out.println("\n========== CREATE SKEY START ==========");
        System.out.println("API Type                 : " + (api != null ? api.getApiType() : null));
        System.out.println("Context                  : " + context);

        validateContext(context);
        validateApi(api);

        System.out.println("Context validation       : SUCCESS");
        System.out.println("API validation           : SUCCESS");

        if (context.getSessionKey() != null && context.getEncryptedSessionKey() != null && !context.getEncryptedSessionKey().isBlank()) {

            System.out.println("CREATE_SKEY already executed.");
            System.out.println("Existing Session Key     : " + Base64.getEncoder().encodeToString(context.getSessionKey()));
            System.out.println("Existing Encrypted Skey  : " + context.getEncryptedSessionKey());
            System.out.println("Existing Certificate ID  : " + context.getCertificateIdentifier());
            System.out.println("========== CREATE SKEY END ==========\n");

            log.info("CREATE_SKEY already executed. apiType={}", api.getApiType());

            return context.getEncryptedSessionKey();
        }

        try {

            log.info("Executing AUA CREATE_SKEY plugin. apiType={}", api.getApiType());

            System.out.println("Creating/Reusing RDEncrypter...");
            RDEncrypter encrypter = getOrCreateEncrypter(context);

            System.out.println("RDEncrypter              : " + encrypter);

            System.out.println("Generating session key...");
            byte[] sessionKey = encrypter.generateSessionKey();

            System.out.println("Generated Session Key    : " + Base64.getEncoder().encodeToString(sessionKey));
            System.out.println("Session Key Length       : " + sessionKey.length + " bytes");

            context.setSessionKey(sessionKey);

            System.out.println("Session key stored in context.");

            String keyIdentifier = UUID.randomUUID().toString();

            System.out.println("Generated Key Identifier : " + keyIdentifier);

            SynchronizedKey synchronizedKey = new SynchronizedKey(sessionKey, keyIdentifier, new Date());

            byte[] syncSessionKey = synchronizedKey.getSeedSkey();

            System.out.println("Synchronized Key Seed    : " + Base64.getEncoder().encodeToString(syncSessionKey));
            System.out.println("Synchronized Key Length  : " + syncSessionKey.length + " bytes");

            System.out.println("Encrypting synchronized session key using UIDAI public key...");

            byte[] encryptedSessionKey = encrypter.encryptUsingPublicKey(syncSessionKey);

            System.out.println("Encrypted Session Key    : " + Base64.getEncoder().encodeToString(encryptedSessionKey));
            System.out.println("Encrypted Key Length     : " + encryptedSessionKey.length + " bytes");

            SessionKeyDetails sessionKeyDetails = SessionKeyDetails.createSkeyToInitializeSynchronizedKey(synchronizedKey.getKeyIdentifier(), encryptedSessionKey);

            System.out.println("SessionKeyDetails created.");
            System.out.println("Key Identifier           : " + synchronizedKey.getKeyIdentifier());
            System.out.println("Skey Value Length        : " + sessionKeyDetails.getSkeyValue().length + " bytes");

            String encodedSessionKey = Base64.getEncoder().encodeToString(sessionKeyDetails.getSkeyValue());

            System.out.println("Final Base64 Skey         : " + encodedSessionKey);
            System.out.println("Final Skey Length         : " + encodedSessionKey.length());

            String certificateIdentifier = encrypter.getCertificateIdentifier();

            System.out.println("Certificate Identifier   : " + certificateIdentifier);

            context.setCertificateIdentifier(certificateIdentifier);
            context.setEncryptedSessionKey(encodedSessionKey);

            System.out.println("Context Session Key       : " + Base64.getEncoder().encodeToString(context.getSessionKey()));

            System.out.println("Context Encrypted Skey    : " + context.getEncryptedSessionKey());

            System.out.println("========== CREATE SKEY END ==========\n");

            log.info("CREATE_SKEY completed. apiType={}, certificateIdentifier={}", api.getApiType(), certificateIdentifier);

            return encodedSessionKey;

        } catch (Exception e) {

            System.out.println("CREATE_SKEY FAILED");
            System.out.println("Exception                : " + e.getMessage());
            e.printStackTrace();

            log.error("CREATE_SKEY failed. apiType={}", api.getApiType(), e);

            throw new IllegalStateException("Failed to create AUA session key", e);
        }
    }

    public String createData(AuaCryptoContext context, AuaApiConfigurationDTO api) {

        System.out.println("\n========== CREATE DATA START ==========");

        System.out.println("API Type                 : " + (api != null ? api.getApiType() : null));

        validateContext(context);
        validateApi(api);

        System.out.println("Context validation       : SUCCESS");
        System.out.println("API validation           : SUCCESS");

        System.out.println("PID XML                  : " + context.getPidXml());

        if (context.getPidXml() == null || context.getPidXml().isBlank()) {

            System.out.println("ERROR: PID XML is empty.");

            throw new IllegalArgumentException("PID XML cannot be empty");
        }

        System.out.println("PID XML Length           : " + context.getPidXml().length());

        byte[] pid = context.getPidXml().getBytes(StandardCharsets.UTF_8);

        System.out.println("PID UTF-8 Bytes Length   : " + pid.length);

        System.out.println("Session Key Present      : " + (context.getSessionKey() != null));

        if (context.getSessionKey() == null) {

            System.out.println("ERROR: Session key is null.");

            throw new IllegalStateException("Session key is required before CREATE_DATA");
        }

        System.out.println("Session Key              : " + Base64.getEncoder().encodeToString(context.getSessionKey()));

        if (context.getEncryptedData() != null && !context.getEncryptedData().isBlank()) {

            System.out.println("CREATE_DATA already executed.");
            System.out.println("Existing Encrypted Data  : " + context.getEncryptedData());
            System.out.println("========== CREATE DATA END ==========\n");

            return context.getEncryptedData();
        }

        try {

            log.info("Executing AUA CREATE_DATA plugin. apiType={}", api.getApiType());

            RDEncrypter encrypter = getOrCreateEncrypter(context);

            System.out.println("RDEncrypter              : " + encrypter);

            String timestamp = getTimestamp(context);

            System.out.println("Crypto Timestamp         : " + timestamp);

            System.out.println("Encrypting PID using session key...");

            byte[] cipherTextWithTS = encrypter.encrypt(pid, context.getSessionKey(), timestamp);

            System.out.println("Encrypted Data Bytes     : " + cipherTextWithTS.length);

            String encryptedData = Base64.getEncoder().encodeToString(cipherTextWithTS);

            System.out.println("Encrypted Data Base64    : " + encryptedData);

            System.out.println("Encrypted Data Length    : " + encryptedData.length());

            context.setEncryptedData(encryptedData);

            System.out.println("Encrypted Data stored in context.");

            System.out.println("========== CREATE DATA END ==========\n");

            log.info("CREATE_DATA completed. apiType={}, encryptedDataLength={}", api.getApiType(), encryptedData.length());

            return encryptedData;

        } catch (Exception e) {

            System.out.println("CREATE_DATA FAILED");
            System.out.println("Exception                : " + e.getMessage());

            e.printStackTrace();

            log.error("CREATE_DATA failed. apiType={}", api.getApiType(), e);

            throw new IllegalStateException("Failed to create encrypted AUA data", e);
        }
    }

    public String createHmac(AuaCryptoContext context, AuaApiConfigurationDTO api) {

        System.out.println("\n========== CREATE HMAC START ==========");

        System.out.println("API Type                 : " + (api != null ? api.getApiType() : null));

        validateContext(context);
        validateApi(api);

        System.out.println("Context validation       : SUCCESS");
        System.out.println("API validation           : SUCCESS");

        System.out.println("PID XML                  : " + context.getPidXml());

        if (context.getPidXml() == null || context.getPidXml().isBlank()) {

            System.out.println("ERROR: PID XML is empty.");

            throw new IllegalArgumentException("PID XML cannot be empty");
        }

        byte[] pid = context.getPidXml().getBytes(StandardCharsets.UTF_8);

        System.out.println("PID UTF-8 Bytes Length   : " + pid.length);

        if (context.getSessionKey() == null) {

            System.out.println("ERROR: Session key is null.");

            throw new IllegalStateException("Session key is required before CREATE_HMAC");
        }

        System.out.println("Session Key              : " + Base64.getEncoder().encodeToString(context.getSessionKey()));

        String timestamp = getTimestamp(context);

        System.out.println("Crypto Timestamp         : " + timestamp);

        if (context.getEncryptedHmac() != null && !context.getEncryptedHmac().isBlank()) {

            System.out.println("CREATE_HMAC already executed.");
            System.out.println("Existing HMAC            : " + context.getEncryptedHmac());
            System.out.println("========== CREATE HMAC END ==========\n");

            return context.getEncryptedHmac();
        }

        try {

            log.info("Executing AUA CREATE_HMAC plugin. apiType={}", api.getApiType());

            RDEncrypter encrypter = getOrCreateEncrypter(context);

            System.out.println("RDEncrypter              : " + encrypter);

            System.out.println("Generating IV...");
            byte[] iv = encrypter.generateIv(timestamp);

            System.out.println("IV Base64                : " + Base64.getEncoder().encodeToString(iv));
            System.out.println("IV Length                : " + iv.length + " bytes");

            System.out.println("Generating AAD...");
            byte[] aad = encrypter.generateAad(timestamp);

            System.out.println("AAD Base64               : " + Base64.getEncoder().encodeToString(aad));
            System.out.println("AAD Length               : " + aad.length + " bytes");

            System.out.println("Generating SHA/hash from PID...");

            byte[] srcHash = encrypter.generateHash(pid);

            System.out.println("PID Hash Base64          : " + Base64.getEncoder().encodeToString(srcHash));
            System.out.println("PID Hash Length          : " + srcHash.length + " bytes");

            System.out.println("Encrypting PID hash using session key...");

            byte[] encryptedHash = encrypter.encryptDecryptUsingSessionKey(true, context.getSessionKey(), iv, aad, srcHash);

            System.out.println("Encrypted HMAC Bytes     : " + encryptedHash.length);

            String encryptedHmac = Base64.getEncoder().encodeToString(encryptedHash);

            System.out.println("Encrypted HMAC Base64    : " + encryptedHmac);

            System.out.println("Encrypted HMAC Length   : " + encryptedHmac.length());

            context.setEncryptedHmac(encryptedHmac);

            System.out.println("Encrypted HMAC stored in context.");

            System.out.println("========== CREATE HMAC END ==========\n");

            log.info("CREATE_HMAC completed. apiType={}, encryptedHmacLength={}", api.getApiType(), encryptedHmac.length());

            return encryptedHmac;

        } catch (Exception e) {

            System.out.println("CREATE_HMAC FAILED");
            System.out.println("Exception                : " + e.getMessage());

            e.printStackTrace();

            log.error("CREATE_HMAC failed. apiType={}", api.getApiType(), e);

            throw new IllegalStateException("Failed to create AUA HMAC", e);
        }
    }

    private RDEncrypter getOrCreateEncrypter(AuaCryptoContext context) {

        System.out.println("\n---------- GET/CREATE ENCRYPTER ----------");

        if (context.getEncrypter() != null) {

            System.out.println("Existing RDEncrypter found.");
            System.out.println("Reusing same RDEncrypter instance.");

            log.info("Reusing existing RDEncrypter.");

            return context.getEncrypter();
        }

        try {

            System.out.println("No RDEncrypter in context.");
            System.out.println("Creating new RDEncrypter...");

            log.info("Creating RDEncrypter for AUA request.");

            CertificateFile certificate = resolveCertificateFile();

            System.out.println("Certificate File         : " + certificate.file().getAbsolutePath());

            System.out.println("Certificate Temporary    : " + certificate.temporary());

            RDEncrypter encrypter = new RDEncrypter(certificate.file().getAbsolutePath());

            System.out.println("RDEncrypter successfully created.");

            context.setCertificateFile(certificate);
            context.setEncrypter(encrypter);

            System.out.println("RDEncrypter stored in context.");

            log.info("RDEncrypter created successfully. temporaryCertificate={}", certificate.temporary());

            return encrypter;

        } catch (Exception e) {

            System.out.println("FAILED TO CREATE RDENCRYPTER");
            System.out.println("Exception                : " + e.getMessage());

            e.printStackTrace();

            log.error("Unable to initialize RDEncrypter", e);

            throw new IllegalStateException("Unable to initialize AUA encryption", e);
        }
    }

    private String getTimestamp(AuaCryptoContext context) {

        if(isNull(context.getTimestamp())){
            throw new IllegalStateException("Timestamp cannot bee null");
        }
        System.out.println("Context TS : " + context.getTimestamp());
        return context.getTimestamp();
    }

    public void cleanup(AuaCryptoContext context) {

        System.out.println("\n========== CRYPTO CLEANUP START ==========");

        if (context == null) {

            System.out.println("Context is NULL. Nothing to cleanup.");
            return;
        }

        CertificateFile certificate = context.getCertificateFile();

        if (certificate != null) {

            System.out.println("Certificate Path         : " + certificate.file().getAbsolutePath());

            System.out.println("Certificate Temporary    : " + certificate.temporary());
        }

        if (certificate != null && certificate.temporary()) {

            System.out.println("Deleting temporary certificate...");

            deleteTemporaryCertificate(certificate);

        } else {

            System.out.println("No temporary certificate deletion required.");
        }

        context.setEncrypter(null);
        context.setCertificateFile(null);

        System.out.println("RDEncrypter cleared from context.");
        System.out.println("Certificate reference cleared.");
        System.out.println("========== CRYPTO CLEANUP END ==========\n");

        log.info("AUA crypto resources cleaned up.");
    }

    private CertificateFile resolveCertificateFile() {

        System.out.println("\n---------- RESOLVE CERTIFICATE ----------");

        try {

            if (publicKeyResource == null) {

                System.out.println("ERROR: UIDAI public key resource is NULL.");

                throw new IllegalArgumentException("UIDAI public key resource is not configured");
            }

            System.out.println("Public Key Resource     : " + publicKeyResource);

            if (!publicKeyResource.exists()) {

                System.out.println("ERROR: UIDAI public key does not exist.");

                throw new IllegalArgumentException("UIDAI public key not found: " + publicKeyResource);
            }

            System.out.println("Public Key Resource exists.");

            if (publicKeyResource.isFile()) {

                File file = publicKeyResource.getFile();

                System.out.println("Using existing certificate.");

                System.out.println("Certificate Path        : " + file.getAbsolutePath());

                log.info("Using existing UIDAI certificate. path={}", file.getAbsolutePath());

                return new CertificateFile(file, false);
            }

            System.out.println("Resource is not a direct file.");

            System.out.println("Creating temporary certificate...");

            File tempFile = File.createTempFile("uidai-auth-", ".cer");

            try (InputStream inputStream = publicKeyResource.getInputStream()) {

                Files.copy(inputStream, tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }

            System.out.println("Temporary Certificate  : " + tempFile.getAbsolutePath());

            log.info("Temporary UIDAI certificate created. path={}", tempFile.getAbsolutePath());

            return new CertificateFile(tempFile, true);

        } catch (Exception e) {

            System.out.println("FAILED TO RESOLVE UIDAI CERTIFICATE");

            System.out.println("Exception                : " + e.getMessage());

            e.printStackTrace();

            log.error("Unable to resolve UIDAI public certificate", e);

            throw new IllegalStateException("Unable to resolve UIDAI public certificate", e);
        }
    }

    private void deleteTemporaryCertificate(CertificateFile certificate) {

        System.out.println("\n---------- DELETE TEMP CERTIFICATE ----------");

        if (certificate == null || !certificate.temporary()) {

            System.out.println("No temporary certificate to delete.");

            return;
        }

        try {

            System.out.println("Deleting certificate       : " + certificate.file().getAbsolutePath());

            Files.deleteIfExists(certificate.file().toPath());

            System.out.println("Temporary certificate deleted.");

            log.info("Temporary UIDAI certificate deleted. path={}", certificate.file().getAbsolutePath());

        } catch (Exception e) {

            System.out.println("Unable to delete temporary certificate.");

            System.out.println("Exception                : " + e.getMessage());

            e.printStackTrace();

            log.warn("Unable to delete temporary UIDAI certificate. path={}", certificate.file().getAbsolutePath(), e);
        }
    }

    private void validateContext(AuaCryptoContext context) {

        System.out.println("Validating AUA crypto context...");

        if (context == null) {

            System.out.println("Context validation FAILED: NULL");

            throw new IllegalArgumentException("AUA crypto context is required");
        }

        System.out.println("Context validation SUCCESS");
    }

    private void validateApi(AuaApiConfigurationDTO api) {

        System.out.println("Validating AUA API configuration...");

        if (api == null) {

            System.out.println("API validation FAILED: NULL");

            throw new IllegalArgumentException("AUA API configuration is required");
        }

        if (api.getApiType() == null || api.getApiType().isBlank()) {

            System.out.println("API validation FAILED: apiType is NULL/EMPTY");

            throw new IllegalArgumentException("AUA API type is required");
        }

        System.out.println("API validation SUCCESS. apiType=" + api.getApiType());
    }

    public record CertificateFile(File file, boolean temporary) {
    }
}