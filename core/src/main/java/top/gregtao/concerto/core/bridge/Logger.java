package top.gregtao.concerto.core.bridge;

public interface Logger {

    interface Factory {
        Logger getLogger(String name);
    }

    void debug(String message);

    void debug(String message, Throwable t);

    void debug(String format, Object... arguments);

    void info(String message);

    void info(String message, Throwable t);

    void info(String format, Object... arguments);

    void error(String message);

    void error(String message, Throwable t);

    void error(String format, Object... arguments);

    void warn(String message);

    void warn(String message, Throwable t);

    void warn(String format, Object... arguments);

}