/*
	Names: Pedro Malavet
	Description: This class used to Help establish a secure PQC channel
*/

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;

/**
 * AES-256-GCM record layer with strict in-order, per-direction replay protection.
 * Each SecureChannel is used with ONE ordered stream in each direction.
 * Both peers must use this protocol version.
 */
public class SecureChannel {
    private static final int IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;
    private static final int MAX_CIPHERTEXT_LENGTH = 16 * 1024 * 1024;
    private final byte[] sendKey;
    private final byte[] receiveKey;
    private final SecureRandom random = new SecureRandom();
    private long sendSequence = 0;
    private long receiveSequence = 0;
    private boolean sendClosed = false;
    private boolean receiveClosed = false;

    public SecureChannel(byte[] sendKey, byte[] receiveKey) {
        if (sendKey == null || sendKey.length != 32)
            throw new IllegalArgumentException("AES-256 send key must be 32 bytes");
        if (receiveKey == null || receiveKey.length != 32)
            throw new IllegalArgumentException("AES-256 receive key must be 32 bytes");
        this.sendKey = sendKey.clone();
        this.receiveKey = receiveKey.clone();
    }

    private static byte[] sequenceAAD(long sequence) {
        return ByteBuffer.allocate(Long.BYTES).putLong(sequence).array();
    }

    private static byte[] crypt(int mode, byte[] key, byte[] input, byte[] iv,
                                long sequence) throws GeneralSecurityException {
        if (iv == null || iv.length != IV_LENGTH)
            throw new GeneralSecurityException("Invalid AES-GCM IV length");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode, new SecretKeySpec(key, "AES"),
                new GCMParameterSpec(GCM_TAG_LENGTH, iv));
        cipher.updateAAD(sequenceAAD(sequence));
        return cipher.doFinal(input);
    }

    /**
     * New wire format (big-endian):
     * [long sequence][int IV length][12-byte IV][int ciphertext length][ciphertext + tag]
     * The sequence number is authenticated as AES-GCM AAD.
     */
    public synchronized void sendEncrypted(DataOutputStream out, byte[] plaintext)
            throws IOException, GeneralSecurityException {
        if (sendClosed || sendSequence < 0 || sendSequence == Long.MAX_VALUE)
            throw new IOException("Sending sequence exhausted or channel unusable; rekey session");
        if (plaintext == null || plaintext.length > MAX_CIPHERTEXT_LENGTH - 16)
            throw new IOException("Plaintext exceeds maximum record size");
        byte[] iv = new byte[IV_LENGTH];
        random.nextBytes(iv);
        long sequence = sendSequence;
        byte[] ciphertext = crypt(Cipher.ENCRYPT_MODE, sendKey, plaintext, iv, sequence);
        try {
            out.writeLong(sequence);
            out.writeInt(iv.length);
            out.write(iv);
            out.writeInt(ciphertext.length);
            out.write(ciphertext);
            out.flush();
            sendSequence++;
        } catch (IOException e) {
            // A partial record cannot be safely retried on this stream.
            sendClosed = true;
            throw e;
        }
    }

    public synchronized byte[] receiveEncrypted(DataInputStream in)
            throws IOException, GeneralSecurityException {
        if (receiveClosed || receiveSequence < 0 || receiveSequence == Long.MAX_VALUE)
            throw new IOException("Receiving sequence exhausted or channel unusable; rekey session");
        try {
            long sequence = in.readLong();
            if (sequence != receiveSequence)
                throw new SecurityException("Replay/out-of-order record: expected sequence "
                        + receiveSequence + ", received " + sequence);
            int ivLength = in.readInt();
            if (ivLength != IV_LENGTH)
                throw new IOException("Invalid AES-GCM IV length: " + ivLength);
            byte[] iv = new byte[ivLength];
            in.readFully(iv);
            int ciphertextLength = in.readInt();
            if (ciphertextLength < 16 || ciphertextLength > MAX_CIPHERTEXT_LENGTH)
                throw new IOException("Invalid ciphertext length: " + ciphertextLength);
            byte[] ciphertext = new byte[ciphertextLength];
            in.readFully(ciphertext);
            byte[] plaintext = crypt(Cipher.DECRYPT_MODE, receiveKey, ciphertext, iv, sequence);
            receiveSequence++;
            return plaintext;
        } catch (IOException | GeneralSecurityException | RuntimeException e) {
            // A failed record is fatal. Do not try to resume reading a possibly
            // desynchronized stream; close the owning socket/session.
            receiveClosed = true;
            throw e;
        }
    }

    public void sendEncryptedString(DataOutputStream out, String message)
            throws IOException, GeneralSecurityException {
        sendEncrypted(out, message.getBytes(StandardCharsets.UTF_8));
    }

    public String receiveEncryptedString(DataInputStream in)
            throws IOException, GeneralSecurityException {
        return new String(receiveEncrypted(in), StandardCharsets.UTF_8);
    }
}
