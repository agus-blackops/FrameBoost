/*
 * Sonora: minimal replacement for androidx.exifinterface's ExifInterface. Media3 only asks for the
 * rotation of artwork images; on API 24+ the platform ExifInterface answers, before that it is 0.
 */
package androidx.exifinterface.media;

import android.os.Build;
import java.io.IOException;
import java.io.InputStream;

public class ExifInterface {
  private final int rotation;

  public ExifInterface(InputStream in) throws IOException {
    int r = 0;
    if (Build.VERSION.SDK_INT >= 24) {
      android.media.ExifInterface exif = new android.media.ExifInterface(in);
      int o = exif.getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1);
      if (o == 6 || o == 7) r = 90;
      else if (o == 3 || o == 4) r = 180;
      else if (o == 8 || o == 5) r = 270;
    }
    rotation = r;
  }

  public int getRotationDegrees() {
    return rotation;
  }
}
