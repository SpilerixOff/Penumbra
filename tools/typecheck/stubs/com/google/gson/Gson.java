package com.google.gson;
import java.io.Reader;
import java.lang.reflect.Type;
public class Gson {
    public String toJson(Object src) { return null; }
    public String toJson(JsonElement element) { return null; }
    public void toJson(Object src, Appendable writer) { }
    public void toJson(JsonElement element, Appendable writer) { }
    public JsonElement toJsonTree(Object src) { return null; }
    public JsonElement toJsonTree(Object src, Type typeOfSrc) { return null; }
    public <T> T fromJson(String json, Class<T> classOfT) { return null; }
    public <T> T fromJson(Reader reader, Class<T> classOfT) { return null; }
    public <T> T fromJson(JsonElement json, Class<T> classOfT) { return null; }
    public <T> T fromJson(String json, Type typeOfT) { return null; }
}
