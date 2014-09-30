package de.qabel.core.crypto;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.SignatureException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.apache.logging.log4j.*;

public class CryptoUtils {

	private final static CryptoUtils INSTANCE = new CryptoUtils();

	private final static String ASYM_KEY_ALGORITHM = "RSA";
	private final static String MESSAGE_DIGEST_ALGORITHM = "SHA-512";
	private final static String SIGNATURE_ALGORITHM = "SHA1withRSA";
	private final static String RSA_CIPHER_ALGORITM = "RSA/ECB/OAEPWITHSHA1ANDMGF1PADDING";
	private final static int RSA_SIGNATURE_SIZE_BYTE = 256;
	private final static int RSA_KEY_SIZE_BIT = 2048;
	private final static String SYMM_KEY_ALGORITHM = "AES";
	private final static String SYMM_TRANSFORMATION = "AES/CTR/NoPadding";
	private final static int SYMM_NONCE_SIZE_BIT = 128;
	private final static int AES_KEY_SIZE_BYTE = 32;
	private final static int ENCRYPTED_AES_KEY_SIZE_BYTE = 256;

	private static Logger logger = LogManager.getLogger(CryptoUtils.class
			.getName());

	private KeyPairGenerator keyGen;
	private SecureRandom secRandom;
	private MessageDigest messageDigest;
	private Cipher symmetricCipher;

	private CryptoUtils() {

		try {
			secRandom = new SecureRandom();

			keyGen = KeyPairGenerator.getInstance(ASYM_KEY_ALGORITHM);
			keyGen.initialize(RSA_KEY_SIZE_BIT);

			messageDigest = MessageDigest.getInstance(MESSAGE_DIGEST_ALGORITHM);
			symmetricCipher = Cipher.getInstance(SYMM_TRANSFORMATION);
		} catch (NoSuchAlgorithmException e) {
			logger.error("Cannot find selected algorithm! " + e.getMessage());
			e.printStackTrace();
		} catch (NoSuchPaddingException e) {
			logger.error("Cannot find selected padding! " + e.getMessage());
			e.printStackTrace();
		}
	}

	public static CryptoUtils getInstance() {
		return CryptoUtils.INSTANCE;
	}

	/**
	 * Returns a new KeyPair
	 * 
	 * @return KeyPair
	 */
	KeyPair generateKeyPair() {
		return keyGen.generateKeyPair();
	}

	/**
	 * Returns a random byte array with an arbitrary size
	 * 
	 * @param numBytes
	 *            Number of random bytes
	 * @return byte[ ] with random bytes
	 */
	byte[] getRandomBytes(int numBytes) {
		byte[] ranBytes = new byte[numBytes];
		secRandom.nextBytes(ranBytes);
		return ranBytes;
	}

	/**
	 * Returns the SHA512 digest for a byte array
	 * 
	 * @param bytes
	 *            byte[ ] to get the digest from
	 * @return byte[ ] with SHA512 digest
	 */
	public byte[] getSHA512sum(byte[] bytes) {
		byte[] digest = messageDigest.digest(bytes);
		return digest;
	}

	/**
	 * Returns the SHA512 digest for a byte array
	 * 
	 * @param bytes
	 *            byte[ ] to get the digest from
	 * @return SHA512 digest as as String in the following format:
	 *         "00:11:aa:bb:..."
	 */
	public String getSHA512sumHumanReadable(byte[] bytes) {
		byte[] digest = getSHA512sum(bytes);

		StringBuilder sb = new StringBuilder(191);

		for (int i = 0; i < digest.length - 1; i++) {
			sb.append(String.format("%02x", digest[i] & 0xff));
			sb.append(":");
		}
		sb.append(String.format("%02x", digest[digest.length - 1] & 0xff));
		return sb.toString();
	}

	/**
	 * Returns the SHA512 digest for a String
	 * 
	 * @param plain
	 *            Input String
	 * @return byte[ ] with SHA512 digest
	 */
	public byte[] getSHA512sum(String plain) {
		return getSHA512sum(plain.getBytes());
	}

	/**
	 * Returns the SHA512 digest for a String
	 * 
	 * @param plain
	 *            Input String
	 * @return SHA512 digest as as String in the following format:
	 *         "00:11:aa:bb:..."
	 */
	public String getSHA512sumHumanReadable(String plain) {
		return getSHA512sumHumanReadable(plain.getBytes());
	}

	/**
	 * Create a signature over the SHA512 sum of message with signature key
	 * 
	 * @param message
	 *            Message to create signature for
	 * @param signatureKey
	 *            Signature key to sign with
	 * @return Signature over SHA512 sum of message
	 */
	private byte[] createSignature(byte[] message, QblSignKeyPair signatureKey) {
		byte[] sha512Sum = getSHA512sum(message);
		return rsaSign(sha512Sum, signatureKey);
	}

	/**
	 * Sign a message with RSA
	 * 
	 * @param message
	 *            Message to sign
	 * @param qpkp
	 *            QblPrimaryKeyPair to extract signature key from
	 * @return Signature over SHA512 sum of message
	 */
	private byte[] rsaSign(byte[] message, QblPrimaryKeyPair qpkp) {
		return rsaSign(message, qpkp.getQblSignPrivateKey());
	}

	/**
	 * Sign a message with RSA
	 * 
	 * @param message
	 *            Message to sign
	 * @param signatureKey
	 *            QblSignKeyPair to extract signature key from
	 * @return Signature over SHA512 sum of message
	 */
	private byte[] rsaSign(byte[] message, QblSignKeyPair signatureKey) {
		return rsaSign(message, signatureKey.getRSAPrivateKey());
	}

	/**
	 * Sign a message with RSA
	 * 
	 * @param message
	 *            Message to sign
	 * @param signatureKey
	 *            QblSignKeyPair to extract signature key from
	 * @return Signature over SHA512 sum of message
	 */
	private byte[] rsaSign(byte[] message, RSAPrivateKey signatureKey) {
		byte[] sign = null;

		Signature signer;
		try {
			signer = Signature.getInstance(SIGNATURE_ALGORITHM);
			signer.initSign(signatureKey);
			signer.update(message);
			sign = signer.sign();
		} catch (InvalidKeyException e) {
			logger.error("Invalid key!");
			e.printStackTrace();
		} catch (NoSuchAlgorithmException e) {
			logger.error(SIGNATURE_ALGORITHM + " not found!");
			e.printStackTrace();
		} catch (SignatureException e) {
			logger.error("Signature exception!");
			e.printStackTrace();
		}
		return sign;
	}

	/**
	 * Signs a sub-key pair with a primary key
	 * 
	 * @param qkp
	 *            Sub-key pair to sign
	 * @param qpkp
	 *            Primary key pair to sign with
	 * @return byte[ ] with the signature
	 */
	byte[] rsaSignKeyPair(QblKeyPair qkp, QblPrimaryKeyPair qpkp) {

		if (qkp == null || qpkp == null) {
			return null;
		}
		return rsaSign(qkp.getPublicKeyFingerprint(), qpkp);
	}

	/**
	 * Validates the signature of a message
	 * 
	 * @param message
	 *            Message to validate signature from
	 * @param signature
	 *            Signature to validate
	 * @param signPublicKey
	 *            Public key to validate signature with
	 * @return is signature valid
	 */
	private boolean validateSignature(byte[] message, byte[] signature,
			QblSignPublicKey signPublicKey) {
		byte[] sha512Sum = getSHA512sum(message);
		return rsaValidateSignature(sha512Sum, signature,
				signPublicKey.getRSAPublicKey());
	}

	/**
	 * Validate the RSA signature of a message
	 * 
	 * @param message
	 *            Message to validate signature from
	 * @param signature
	 *            Signature to validate
	 * @param signatureKey
	 *            Public key to validate signature with
	 * @return is signature valid
	 */
	private boolean rsaValidateSignature(byte[] message, byte[] signature,
			RSAPublicKey signatureKey) {
		boolean isValid = false;
		try {
			Signature signer = Signature.getInstance(SIGNATURE_ALGORITHM);
			signer.initVerify(signatureKey);
			signer.update(message);
			isValid = signer.verify(signature);
		} catch (InvalidKeyException e) {
			logger.error("Invalid key!");
			e.printStackTrace();
		} catch (NoSuchAlgorithmException e) {
			logger.error(SIGNATURE_ALGORITHM + " not found!");
			e.printStackTrace();
		} catch (SignatureException e) {
			logger.error("Signature exception!");
			e.printStackTrace();
		}
		return isValid;
	}

	/**
	 * Validates a signature from a sub-public key with a primary public key
	 * 
	 * @param subKey
	 *            Sub-public key to validate
	 * @param primaryKey
	 *            Primary public key to validate signature with
	 * @return is signature valid
	 */
	boolean rsaValidateKeySignature(QblSubPublicKey subKey,
			QblPrimaryPublicKey primaryKey) {

		if (subKey == null || primaryKey == null) {
			return false;
		}
		return rsaValidateSignature(subKey.getPublicKeyFingerprint(),
				subKey.getPrimaryKeySignature(), primaryKey.getRSAPublicKey());
	}

	/**
	 * Encrypts a byte[ ] with RSA
	 * 
	 * @param message
	 *            message to encrypt
	 * @param reciPubKey
	 *            public key to encrypt with
	 * @return encrypted messsage
	 */
	private byte[] rsaEncryptForRecipient(byte[] message, QblEncPublicKey reciPubKey) {
		byte[] cipherText = null;
		try {
			Cipher cipher = Cipher.getInstance(RSA_CIPHER_ALGORITM);
			cipher.init(Cipher.ENCRYPT_MODE, reciPubKey.getRSAPublicKey(),
					secRandom);
			cipherText = cipher.doFinal(message);
		} catch (InvalidKeyException e) {
			logger.error("Invalid RSA public key!");
			e.printStackTrace();
		} catch (IllegalBlockSizeException e) {
			logger.error("Illegal block size!");
			e.printStackTrace();
		} catch (BadPaddingException e) {
			logger.error("Bad padding!");
			e.printStackTrace();
		} catch (NoSuchAlgorithmException e) {
			logger.error("Algorithm " + RSA_CIPHER_ALGORITM + " not found!");
			e.printStackTrace();
		} catch (NoSuchPaddingException e) {
			logger.error("Padding " + RSA_CIPHER_ALGORITM + " not found!");
			e.printStackTrace();
		}
		return cipherText;
	}

	/**
	 * Decrypts a RSA encrypted ciphertext
	 * 
	 * @param cipherText
	 *            ciphertext to decrypt
	 * @param privKey
	 *            private key to decrypt with
	 * @return decrypted ciphertext, or null if undecryptable
	 */
	private byte[] rsaDecrypt(byte[] cipherText, RSAPrivateKey privKey) {
		byte[] plaintext = null;
		try {
			Cipher cipher = Cipher.getInstance(RSA_CIPHER_ALGORITM);
			cipher.init(Cipher.DECRYPT_MODE, privKey, secRandom);
			plaintext = cipher.doFinal(cipherText);
		} catch (InvalidKeyException e) {
			logger.error("Invalid RSA private key!");
			e.printStackTrace();
		} catch (IllegalBlockSizeException e) {
			logger.error("Illegal block size!");
			e.printStackTrace();
		} catch (NoSuchAlgorithmException e) {
			logger.error("Algorithm " + RSA_CIPHER_ALGORITM + " not found!");
			e.printStackTrace();
		} catch (NoSuchPaddingException e) {
			logger.error("Padding " + RSA_CIPHER_ALGORITM + " not found!");
			e.printStackTrace();
		} catch (BadPaddingException e) {
			// This exception should occur if cipherText is decrypted with wrong
			// private key
			return null;
		}
		return plaintext;
	}

	/**
	 * Returns the encrypted byte[] of the given plain text, i.e.
	 * ciphertext=enc(plaintext,key) The algorithm, mode and padding is set in
	 * constant SYMM_TRANSFORMATION
	 * 
	 * @param plainText
	 *            message which will be encrypted
	 * @param key
	 *            symmetric key which is used for en- and decryption
	 * @return cipher text which is the result of the encryption
	 */
	byte[] symmEncrypt(byte[] plainText, byte[] key) {
		byte[] rand;
		ByteArrayOutputStream cipherText = new ByteArrayOutputStream();
		IvParameterSpec nonce;

		rand = getRandomBytes(SYMM_NONCE_SIZE_BIT / 8);
		nonce = new IvParameterSpec(rand);

		SecretKeySpec symmetricKey = new SecretKeySpec(key, SYMM_KEY_ALGORITHM);

		try {
			cipherText.write(rand);
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		try {
			symmetricCipher.init(Cipher.ENCRYPT_MODE, symmetricKey, nonce);
			cipherText.write(symmetricCipher.doFinal(plainText));
		} catch (InvalidKeyException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InvalidAlgorithmParameterException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IllegalBlockSizeException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (BadPaddingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return cipherText.toByteArray();
	}

	/**
	 * Returns the plain text of the encrypted input
	 * plaintext=enc⁻¹(ciphertext,key) The algorithm, mode and padding is set in
	 * constant SYMM_TRANSFORMATION
	 * 
	 * @param cipherText
	 *            encrypted message which will be decrypted
	 * @param key
	 *            symmetric key which is used for en- and decryption
	 * @return plain text which is the result of the decryption
	 */
	byte[] symmDecrypt(byte[] cipherText, byte[] key) {
		ByteArrayInputStream bi = new ByteArrayInputStream(cipherText);
		byte[] rand = new byte[SYMM_NONCE_SIZE_BIT / 8];
		byte[] encryptedPlainText = new byte[cipherText.length
				- SYMM_NONCE_SIZE_BIT / 8];
		byte[] plainText = null;
		IvParameterSpec nonce;
		SecretKeySpec symmetricKey;

		try {
			bi.read(rand);
			bi.read(encryptedPlainText);
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}

		nonce = new IvParameterSpec(rand);
		symmetricKey = new SecretKeySpec(key, SYMM_KEY_ALGORITHM);

		try {
			symmetricCipher.init(Cipher.DECRYPT_MODE, symmetricKey, nonce);
			plainText = symmetricCipher.doFinal(encryptedPlainText);
		} catch (InvalidKeyException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InvalidAlgorithmParameterException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IllegalBlockSizeException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (BadPaddingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return plainText;
	}

	/**
	 * Hybrid encrypts a String message for a recipient. The String message is
	 * encrypted with a random AES key. The AES key gets RSA encrypted with the
	 * recipients public key.
	 * 
	 * @param message
	 *            String message to encrypt
	 * @param recipient
	 *            Recipient to encrypt message for
	 * @return hybrid encrypted String message
	 */
	public byte[] encryptMessage(String message, QblEncPublicKey recipient,
			QblSignKeyPair signatureKey) {
		ByteArrayOutputStream bs = new ByteArrayOutputStream();
		byte[] aesKey = getRandomBytes(AES_KEY_SIZE_BYTE);

		try {
			bs.write(rsaEncryptForRecipient(aesKey, recipient));
			bs.write(symmEncrypt(message.getBytes(), aesKey));
			bs.write(createSignature(bs.toByteArray(), signatureKey));
		} catch (IOException e) {
			logger.error("IOException while writing to ByteArrayOutputStream");
			e.printStackTrace();
		}
		return bs.toByteArray();
	}

	/**
	 * Decrypts a hybrid encrypted String message. The AES key is decrypted
	 * using the own private key. The decrypted AES key is used to decrypt the
	 * String message
	 * 
	 * @param cipherText
	 *            hybrid encrypted String message
	 * @param privKey
	 *            private key to encrypt String message with
	 * @return decrypted String message or null if message is undecryptable
	 */
	public String decryptMessage(byte[] cipherText, QblPrimaryKeyPair privKey,
			QblSignPublicKey signatureKey) {
		ByteArrayInputStream bs = new ByteArrayInputStream(cipherText);
		// TODO: Include header byte
		// Get RSA encrypted AES key and encrypted data and signature over the
		// RSA
		// encrypted AES key and encrypted data
		byte[] encryptedMessage = new byte[bs.available()
				- RSA_SIGNATURE_SIZE_BYTE];
		byte[] rsaSignature = new byte[RSA_SIGNATURE_SIZE_BYTE];
		try {
			bs.read(encryptedMessage);
			bs.read(rsaSignature);
		} catch (IOException e) {
			logger.error("IOException while reading from ByteArrayInputStream");
			e.printStackTrace();
		}

		// Validate signature over RSA encrypted AES key and encrypted data
		if (!validateSignature(encryptedMessage, rsaSignature, signatureKey)) {
			logger.debug("Message signature invalid!");
			return null;
		}

		// Read RSA encrypted AES key and encryptedData
		bs = new ByteArrayInputStream(encryptedMessage);

		byte[] encryptedAesKey = new byte[ENCRYPTED_AES_KEY_SIZE_BYTE];
		byte[] aesCipherText = new byte[bs.available()
				- ENCRYPTED_AES_KEY_SIZE_BYTE];

		try {
			bs.read(encryptedAesKey);
			bs.read(aesCipherText);
		} catch (IOException e) {
			logger.error("IOException while reading from ByteArrayInputStream");
			e.printStackTrace();
		}

		// Decrypt RSA encrypted AES key and decrypt encrypted data with AES key
		byte[] aesKey = rsaDecrypt(encryptedAesKey,
				privKey.getQblEncPrivateKey());
		if (aesKey != null) {
			return new String(symmDecrypt(aesCipherText, aesKey));
		}
		return null;
	}
}
