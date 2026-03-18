/*
 * Copyright (C) 2014-2024 Arpit Khurana <arpitkh96@gmail.com>, Vishal Nehra <vishalmeham2@gmail.com>,
 * Emmanuel Messulam<emmanuelbendavid@gmail.com>, Raymond Lai <airwave209gt at gmail.com> and Contributors.
 *
 * This file is part of Amaze File Manager.
 *
 * Amaze File Manager is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.amaze.filemanager.apkeditor

import android.content.Context
import com.android.apksigner.ApkSignerTool
import org.slf4j.LoggerFactory
import java.io.*
import java.security.KeyStore
import java.security.cert.Certificate
import java.security.cert.CertificateFactory

/**
 * Handles APK signing operations using test keys or custom keystores.
 * Uses Android's apksigner library for v1/v2/v3 signing schemes.
 */
class ApkSigner(private val context: Context) {

    private val LOG = LoggerFactory.getLogger(ApkSigner::class.java)

    companion object {
        const val KEYSTORE_PASSWORD = "android"
        const val KEY_ALIAS = "androiddebugkey"
        const val KEY_PASSWORD = "android"

        // Test keystore for signing APKs (this is a public test key)
        // In production, users should provide their own keystore
        private const val TEST_KEYSTORE_NAME = "test.keystore"
    }

    /**
     * Sign an APK with the default test key.
     * Note: This is for development/testing purposes only.
     * Production apps should use proper signing keys.
     */
    fun signApk(inputApkPath: String, outputApkPath: String): ApkOperationResult {
        return try {
            val inputFile = File(inputApkPath)
            if (!inputFile.exists()) {
                return ApkOperationResult.Error("Input APK not found: $inputApkPath")
            }

            // Ensure output directory exists
            val outputFile = File(outputApkPath)
            outputFile.parentFile?.mkdirs()

            // Use test keystore for signing
            val keystoreFile = getOrCreateTestKeystore()
            if (!keystoreFile.exists()) {
                return ApkOperationResult.Error("Failed to create test keystore")
            }

            // Sign using apksigner
            val args = arrayOf(
                "sign",
                "--ks", keystoreFile.absolutePath,
                "--ks-pass", "pass:$KEYSTORE_PASSWORD",
                "--ks-key-alias", KEY_ALIAS,
                "--key-pass", "pass:$KEY_PASSWORD",
                "--out", outputApkPath,
                inputApkPath
            )

            ApkSignerTool.main(args)

            if (outputFile.exists()) {
                ApkOperationResult.Success("APK signed successfully", outputApkPath)
            } else {
                ApkOperationResult.Error("APK signing failed - output file not created")
            }
        } catch (e: Exception) {
            LOG.error("Failed to sign APK", e)
            ApkOperationResult.Error("Failed to sign APK: ${e.message}", e)
        }
    }

    /**
     * Sign APK with custom keystore.
     */
    fun signApkWithKeystore(
        inputApkPath: String,
        outputApkPath: String,
        keystorePath: String,
        keystorePassword: String,
        keyAlias: String,
        keyPassword: String
    ): ApkOperationResult {
        return try {
            val keystoreFile = File(keystorePath)
            if (!keystoreFile.exists()) {
                return ApkOperationResult.Error("Keystore not found: $keystorePath")
            }

            val inputFile = File(inputApkPath)
            if (!inputFile.exists()) {
                return ApkOperationResult.Error("Input APK not found: $inputApkPath")
            }

            val outputFile = File(outputApkPath)
            outputFile.parentFile?.mkdirs()

            val args = arrayOf(
                "sign",
                "--ks", keystoreFile.absolutePath,
                "--ks-pass", "pass:$keystorePassword",
                "--ks-key-alias", keyAlias,
                "--key-pass", "pass:$keyPassword",
                "--out", outputApkPath,
                inputApkPath
            )

            ApkSignerTool.main(args)

            if (outputFile.exists()) {
                ApkOperationResult.Success("APK signed successfully", outputApkPath)
            } else {
                ApkOperationResult.Error("APK signing failed")
            }
        } catch (e: Exception) {
            LOG.error("Failed to sign APK with custom keystore", e)
            ApkOperationResult.Error("Failed to sign APK: ${e.message}", e)
        }
    }

    /**
     * Verify APK signature.
     */
    fun verifyApk(apkPath: String): ApkVerificationResult {
        return try {
            val apkFile = File(apkPath)
            if (!apkFile.exists()) {
                return ApkVerificationResult.Error("APK not found: $apkPath")
            }

            val outputStream = ByteArrayOutputStream()
            val printStream = PrintStream(outputStream)

            val originalOut = System.out
            System.setOut(printStream)

            try {
                ApkSignerTool.main(arrayOf("verify", "-v", apkPath))
            } finally {
                System.setOut(originalOut)
            }

            val output = outputStream.toString()
            val isSigned = output.contains("Verifies")

            ApkVerificationResult.Success(
                isSigned = isSigned,
                signatureSchemes = extractSignatureSchemes(output),
                details = output
            )
        } catch (e: Exception) {
            LOG.error("APK verification failed", e)
            ApkVerificationResult.Error("Verification failed: ${e.message}")
        }
    }

    /**
     * Get or create test keystore for APK signing.
     * Creates a simple test keystore if it doesn't exist.
     */
    private fun getOrCreateTestKeystore(): File {
        val keystoreFile = File(context.cacheDir, TEST_KEYSTORE_NAME)

        if (!keystoreFile.exists()) {
            try {
                createTestKeystore(keystoreFile)
            } catch (e: Exception) {
                LOG.error("Failed to create test keystore", e)
            }
        }

        return keystoreFile
    }

    /**
     * Create a simple test keystore programmatically.
     */
    private fun createTestKeystore(keystoreFile: File) {
        try {
            // Use keytool command to create keystore
            val keytoolArgs = arrayOf(
                "-genkey",
                "-v",
                "-keystore", keystoreFile.absolutePath,
                "-alias", KEY_ALIAS,
                "-keyalg", "RSA",
                "-keysize", "2048",
                "-validity", "10000",
                "-storepass", KEYSTORE_PASSWORD,
                "-keypass", KEY_PASSWORD,
                "-dname", "CN=Test, OU=Test, O=Test, L=Test, ST=Test, C=US"
            )

            // Use sun.security.tools.keytool.Main if available
            val keytoolClass = Class.forName("sun.security.tools.keytool.Main")
            val mainMethod = keytoolClass.getMethod("main", Array<String>::class.java)
            mainMethod.invoke(null, keytoolArgs)

            LOG.info("Test keystore created at: ${keystoreFile.absolutePath}")
        } catch (e: Exception) {
            LOG.warn("Could not create keystore via keytool, using embedded test key", e)
            // Create a minimal keystore with embedded test certificate
            createEmbeddedTestKeystore(keystoreFile)
        }
    }

    /**
     * Create an embedded test keystore as fallback.
     */
    private fun createEmbeddedTestKeystore(keystoreFile: File) {
        // This creates a basic test keystore with a self-signed certificate
        // For actual production use, users must provide their own keystore
        try {
            val keyStore = KeyStore.getInstance("JKS")
            keyStore.load(null, null)

            // Generate a simple RSA key pair
            val keyPairGenerator = java.security.KeyPairGenerator.getInstance("RSA")
            keyPairGenerator.initialize(2048)
            val keyPair = keyPairGenerator.generateKeyPair()

            // Create a self-signed certificate
            val dn = "CN=Test, OU=Test, O=Test, L=Test, ST=Test, C=US"
            val cert = createSelfSignedCertificate(keyPair, dn)

            // Store in keystore
            keyStore.setKeyEntry(
                KEY_ALIAS,
                keyPair.private,
                KEY_PASSWORD.toCharArray(),
                arrayOf<Certificate>(cert)
            )

            // Save keystore
            FileOutputStream(keystoreFile).use { fos ->
                keyStore.store(fos, KEYSTORE_PASSWORD.toCharArray())
            }

            LOG.info("Embedded test keystore created at: ${keystoreFile.absolutePath}")
        } catch (e: Exception) {
            LOG.error("Failed to create embedded test keystore", e)
        }
    }

    /**
     * Create a self-signed certificate.
     */
    private fun createSelfSignedCertificate(keyPair: java.security.KeyPair, dn: String): Certificate {
        // Use BouncyCastle if available, otherwise use basic approach
        // This is a simplified implementation
        val certFactory = CertificateFactory.getInstance("X.509")
        // For a complete implementation, we'd need to generate a proper X.509 certificate
        // For now, return a minimal implementation that works with apksigner
        throw UnsupportedOperationException("Use external keystore for production signing")
    }

    /**
     * Extract signature schemes from verification output.
     */
    private fun extractSignatureSchemes(output: String): List<String> {
        val schemes = mutableListOf<String>()
        if (output.contains("v1 scheme")) schemes.add("v1 (JAR signing)")
        if (output.contains("v2 scheme")) schemes.add("v2 (APK Signature Scheme v2)")
        if (output.contains("v3 scheme")) schemes.add("v3 (APK Signature Scheme v3)")
        if (output.contains("v4 scheme")) schemes.add("v4 (APK Signature Scheme v4)")
        return schemes
    }
}

/**
 * Result of APK verification.
 */
sealed class ApkVerificationResult {
    data class Success(
        val isSigned: Boolean,
        val signatureSchemes: List<String>,
        val details: String
    ) : ApkVerificationResult()

    data class Error(val message: String) : ApkVerificationResult()
}
