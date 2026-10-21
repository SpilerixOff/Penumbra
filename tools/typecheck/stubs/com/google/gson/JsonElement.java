package com.google.gson;
public abstract class JsonElement {
    public JsonElement deepCopy() { return null; }
    public JsonObject getAsJsonObject() { return null; }
    public JsonArray getAsJsonArray() { return null; }
    public boolean isJsonObject() { return false; }
    public boolean isJsonArray() { return false; }
    public boolean isJsonNull() { return false; }
    public boolean isJsonPrimitive() { return false; }
    public String getAsString() { return null; }
    public int getAsInt() { return 0; }
    public long getAsLong() { return 0L; }
    public float getAsFloat() { return 0f; }
    public double getAsDouble() { return 0d; }
    public boolean getAsBoolean() { return false; }
}
