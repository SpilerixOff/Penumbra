package com.google.gson;
import java.util.Map;
import java.util.Set;
public class JsonObject extends JsonElement {
    @Override public JsonObject deepCopy() { return null; }
    public void add(String property, JsonElement value) { }
    public void addProperty(String property, String value) { }
    public void addProperty(String property, Number value) { }
    public void addProperty(String property, Boolean value) { }
    public JsonElement get(String property) { return null; }
    public JsonElement remove(String property) { return null; }
    public boolean has(String property) { return false; }
    public int size() { return 0; }
    public Set<Map.Entry<String, JsonElement>> entrySet() { return null; }
    public Set<String> keySet() { return null; }
}
