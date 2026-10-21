package org.slf4j;
public interface Logger {
    void info(String message, Object... args);
    void warn(String message, Object... args);
    void error(String message, Object... args);
    void debug(String message, Object... args);
    void trace(String message, Object... args);
    void info(String message, Throwable t);
    void warn(String message, Throwable t);
    void error(String message, Throwable t);
    boolean isDebugEnabled();
}
