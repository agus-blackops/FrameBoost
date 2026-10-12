package com.frameboost.ragdoll;

/** Player preferences (persisted by the Activity in SharedPreferences). */
final class Settings {
    float music = 0.7f, sfx = 0.9f;
    boolean haptics = true, gore = true, showFps = false, highQuality = true, tutorialDone = false;
    String stars = "000000";

    int stars(int i) { return i < stars.length() ? stars.charAt(i) - '0' : 0; }

    void setStars(int i, int s) {
        StringBuilder b = new StringBuilder(stars);
        while (b.length() <= i) b.append('0');
        if (s > b.charAt(i) - '0') b.setCharAt(i, (char) ('0' + s));
        stars = b.toString();
    }

    int totalStars() { int t = 0; for (int i = 0; i < stars.length(); i++) t += stars.charAt(i) - '0'; return t; }
}
