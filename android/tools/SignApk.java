import com.android.apksig.ApkSigner;
import com.android.apksig.ApkVerifier;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Adds classes.dex to the APK aapt2 linked, signs it with APK Signature Scheme v2 using apksig,
 * and verifies the result.
 *
 * <p>v2 only: every Android this app supports (8.0+) verifies v2, and the v1 (JAR) signer in
 * apksig 2.3.0 calls JDK internals that no longer exist.
 *
 * <pre>
 * java -cp apksig.jar:. SignApk linked.apk classes.dex keystore.p12 password out.apk min-sdk
 * </pre>
 *
 * Stored entries keep their compression method and are aligned to 4 bytes here, as zipalign
 * would: Android 11+ refuses an app targeting API 30+ whose resources.arsc is not. apksig then
 * preserves that alignment when it signs.
 */
public class SignApk {
    public static void main(String[] args) throws Exception {
        if (args.length != 6) {
            System.err.println("usage: SignApk linked.apk classes.dex keystore.p12 password out.apk min-sdk");
            System.exit(2);
        }
        File linked = new File(args[0]);
        File dex = new File(args[1]);
        File keystore = new File(args[2]);
        char[] password = args[3].toCharArray();
        File out = new File(args[4]);
        int minSdk = Integer.parseInt(args[5]);

        File merged = File.createTempFile("merged", ".apk", out.getAbsoluteFile().getParentFile());
        try {
            addDex(linked, dex, merged);

            KeyStore ks = KeyStore.getInstance("PKCS12");
            try (InputStream in = new FileInputStream(keystore)) {
                ks.load(in, password);
            }
            String alias = ks.aliases().nextElement();
            PrivateKey key = (PrivateKey) ks.getKey(alias, password);
            List<X509Certificate> chain = new ArrayList<>();
            for (Certificate c : ks.getCertificateChain(alias)) chain.add((X509Certificate) c);

            ApkSigner.SignerConfig signer =
                    new ApkSigner.SignerConfig.Builder("frameboost", key, chain).build();
            new ApkSigner.Builder(Collections.singletonList(signer))
                    .setInputApk(merged)
                    .setOutputApk(out)
                    .setMinSdkVersion(minSdk)
                    .setV1SigningEnabled(false)
                    .setV2SigningEnabled(true)
                    .build()
                    .sign();
        } finally {
            merged.delete();
        }

        ApkVerifier.Result r = new ApkVerifier.Builder(out).setMinCheckedPlatformVersion(minSdk).build().verify();
        if (!r.isVerified()) {
            for (Object e : r.getErrors()) System.err.println("error: " + e);
            System.exit(1);
        }
        System.out.println("signed and verified: v1=" + r.isVerifiedUsingV1Scheme()
                + " v2=" + r.isVerifiedUsingV2Scheme() + " -> " + out);
    }

    /** zipalign's padding: header 0xd935, the alignment, then zeros up to the boundary. */
    private static byte[] alignmentExtra(long dataOffsetWithoutExtra, int alignment) {
        int min = 6;
        int pad = (int) ((alignment - (dataOffsetWithoutExtra + min) % alignment) % alignment);
        byte[] extra = new byte[min + pad];
        int size = 2 + pad;
        extra[0] = (byte) 0x35;
        extra[1] = (byte) 0xd9;
        extra[2] = (byte) size;
        extra[3] = (byte) (size >> 8);
        extra[4] = (byte) alignment;
        extra[5] = (byte) (alignment >> 8);
        return extra;
    }

    private static final class CountingOutputStream extends FilterOutputStream {
        long count;

        CountingOutputStream(OutputStream out) {
            super(out);
        }

        @Override
        public void write(int b) throws IOException {
            out.write(b);
            count++;
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            out.write(b, off, len);
            count += len;
        }
    }

    private static void addDex(File apk, File dex, File out) throws Exception {
        try (ZipFile in = new ZipFile(apk);
             CountingOutputStream counter = new CountingOutputStream(new FileOutputStream(out));
             ZipOutputStream zos = new ZipOutputStream(counter)) {
            Enumeration<? extends ZipEntry> entries = in.entries();
            while (entries.hasMoreElements()) {
                ZipEntry e = entries.nextElement();
                if (e.getName().equals("classes.dex")) continue;
                ZipEntry copy = new ZipEntry(e.getName());
                byte[] data;
                try (InputStream is = in.getInputStream(e)) {
                    data = is.readAllBytes();
                }
                if (e.getMethod() == ZipEntry.STORED) {
                    copy.setMethod(ZipEntry.STORED);
                    copy.setSize(data.length);
                    copy.setCompressedSize(data.length);
                    copy.setCrc(e.getCrc());
                    // Everything before this entry is on disk once the previous
                    // entry is closed, so the counter is the header's offset.
                    int name = copy.getName().getBytes(StandardCharsets.UTF_8).length;
                    copy.setExtra(alignmentExtra(counter.count + 30 + name, 4));
                }
                zos.putNextEntry(copy);
                zos.write(data);
                zos.closeEntry();
            }
            byte[] d = Files.readAllBytes(dex.toPath());
            ZipEntry entry = new ZipEntry("classes.dex");
            zos.putNextEntry(entry);
            zos.write(d);
            zos.closeEntry();
        }
    }
}
