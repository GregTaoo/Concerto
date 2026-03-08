package top.gregtao.concerto.bridge;

import org.slf4j.LoggerFactory;
import top.gregtao.concerto.core.bridge.Logger;

public class LoggerImpl implements Logger {

    private final org.slf4j.Logger logger;

    public LoggerImpl(String name) {
        this.logger = LoggerFactory.getLogger(name);
    }

    @Override
    public void debug(String message) {
        this.logger.debug(message);
    }

    @Override
    public void debug(String message, Throwable t) {
        this.logger.debug(message, t);
    }

    @Override
    public void debug(String format, Object... arguments) {
        this.logger.debug(format, arguments);
    }

    @Override
    public void info(String message) {
        this.logger.info(message);
    }

    @Override
    public void info(String message, Throwable t) {
        this.logger.info(message, t);
    }

    @Override
    public void info(String format, Object... arguments) {
        this.logger.info(format, arguments);
    }

    @Override
    public void error(String message) {
        this.logger.error(message);
    }

    @Override
    public void error(String message, Throwable t) {
        this.logger.error(message, t);
    }

    @Override
    public void error(String format, Object... arguments) {
        this.logger.error(format, arguments);
    }

    @Override
    public void warn(String message) {
        this.logger.warn(message);
    }

    @Override
    public void warn(String message, Throwable t) {
        this.logger.warn(message, t);
    }

    @Override
    public void warn(String format, Object... arguments) {
        this.logger.warn(format, arguments);
    }
}
