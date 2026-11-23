package io.hiddemoens.pluskey;

import android.content.Context;
import android.os.Build;
import java.io.*;
import java.math.BigInteger;
import java.nio.file.Files;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Date;
import java.util.concurrent.TimeUnit;
import javax.security.auth.x500.X500Principal;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import io.github.muntashirakon.adb.AbsAdbConnectionManager;

/** One persistent identity, stored only in app-private storage (backup disabled). */
final class AdbIdentity extends AbsAdbConnectionManager {
    private final PrivateKey privateKey;
    private final Certificate certificate;
    AdbIdentity(Context context) throws Exception {
        setApi(Build.VERSION.SDK_INT);
        setTimeout(8, TimeUnit.SECONDS);
        setThrowOnUnauthorised(true);
        File file = new File(context.getFilesDir(), "wireless-adb.identity");
        if (file.exists()) {
            try (DataInputStream input = new DataInputStream(new FileInputStream(file))) {
                int size = input.readInt();
                if (size < 1 || size > 16384) throw new IOException("Saved pairing identity is invalid");
                byte[] bytes = new byte[size]; input.readFully(bytes);
                privateKey = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(bytes));
                certificate = CertificateFactory.getInstance("X.509").generateCertificate(input);
            }
        } else {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            java.security.KeyPair pair = generator.generateKeyPair();
            privateKey = pair.getPrivate();
            X500Principal name = new X500Principal("CN=Odin");
            long now = System.currentTimeMillis();
            byte[] encoded = new JcaX509v3CertificateBuilder(name, new BigInteger(128, new SecureRandom()),
                new Date(now - 86400000L), new Date(now + 315360000000L), name, pair.getPublic())
                .build(new JcaContentSignerBuilder("SHA256withRSA").build(privateKey)).getEncoded();
            certificate = CertificateFactory.getInstance("X.509").generateCertificate(new ByteArrayInputStream(encoded));
            File next = new File(context.getFilesDir(), "wireless-adb.identity.next");
            try (DataOutputStream output = new DataOutputStream(new FileOutputStream(next))) {
                byte[] key = privateKey.getEncoded();
                output.writeInt(key.length); output.write(key); output.write(encoded);
            }
            if (!next.renameTo(file)) throw new IOException("Cannot save pairing identity");
        }
    }
    protected PrivateKey getPrivateKey() { return privateKey; }
    protected Certificate getCertificate() { return certificate; }
    protected String getDeviceName() { return "Odin"; }
}
