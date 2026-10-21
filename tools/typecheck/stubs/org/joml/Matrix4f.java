package org.joml;
public class Matrix4f implements Matrix4fc {
    public Matrix4f() { }
    public Matrix4f(Matrix4fc other) { }
    public Matrix4f translate(float x, float y, float z) { return this; }
    public Matrix4f scale(float x, float y, float z) { return this; }
    public Matrix4f rotate(Quaternionfc quat) { return this; }
    public Matrix4f mul(Matrix4fc other) { return this; }
}
