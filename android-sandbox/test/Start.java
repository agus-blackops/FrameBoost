package com.frameboost.ragdoll;
public class Start { public static void main(String[] a) throws Exception {
  Game g = new Game(null); g.resize(2400,1080,2.75f); Harness.run(g, 2.5f); Harness.shot(g, a[0]+"/start.png"); } }
