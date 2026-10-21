package org.joml;
public class Vector3f implements Vector3fc {
    public float x, y, z;
    public Vector3f() { }
    public Vector3f(float x, float y, float z) { this.x = x; this.y = y; this.z = z; }
    public Vector3f(Vector3fc other) { }
    public float x() { return x; }
    public float y() { return y; }
    public float z() { return z; }
    public Vector3f set(float x, float y, float z) { return this; }
    public Vector3f add(float x, float y, float z) { return this; }
    public Vector3f mul(float scalar) { return this; }
    public Vector3f normalize() { return this; }
    public float length() { return 0f; }
    public Vector3f rotate(Quaternionfc quat) { return this; }
}
