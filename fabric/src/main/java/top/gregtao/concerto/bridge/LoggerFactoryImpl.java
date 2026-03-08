package top.gregtao.concerto.bridge;

import top.gregtao.concerto.core.bridge.Logger;
import top.gregtao.concerto.core.bridge.LoggerFactory;

public class LoggerFactoryImpl implements LoggerFactory {

    @Override
    public Logger getLogger(String name) {
        return new LoggerImpl(name);
    }
}
