package com.karaokeflow.app;
public final class SfSynth {
 static {System.loadLibrary("karaokeflow_synth");}
 public static native boolean load(String path);
 public static native void event(int status,int a,int b);
 public static native void render(short[] output,int frames);
 public static native void reset();
 public static native void close();
}
