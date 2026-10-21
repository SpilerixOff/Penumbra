package org.joml;
public class Quaternionf implements Quaternionfc {
    public float x, y, z, w;
    public Quaternionf() { }
    public Quaternionf(float x, float y, float z, float w) { }
    public float x() { return x; }
    public float y() { return y; }
    public float z() { return z; }
    public float w() { return w; }
    public Quaternionf rotationXYZ(float x, float y, float z) { return this; }
    public Quaternionf mul(Quaternionfc other) { return this; }
}
