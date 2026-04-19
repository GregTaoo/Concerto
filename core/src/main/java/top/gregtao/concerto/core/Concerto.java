package top.gregtao.concerto.core;

import top.gregtao.concerto.core.bridge.CoreBridge;
import top.gregtao.concerto.core.bridge.Logger;
import top.gregtao.concerto.core.config.ConfigFile;

public class Concerto {

    public static final String MOD_ID = "concerto";

    private static CoreBridge CORE_BRIDGE;
    private static Logger LOGGER;
    private static Logger.Factory LOGGER_FACTORY;

    public static final ConfigFile MUSIC_CONFIG = new ConfigFile("Concerto/musics.json");

    public static void registerCoreBridge(CoreBridge mc, Logger.Factory loggerFactory) {
        CORE_BRIDGE = mc;
        LOGGER_FACTORY = loggerFactory;
        LOGGER = loggerFactory.getLogger("Concerto");
    }

    public static CoreBridge getCoreBridge() {
        if (CORE_BRIDGE == null)
            throw new NullPointerException("CoreBridge was not initialized");
        return CORE_BRIDGE;
    }

    public static Logger.Factory getLoggerFactory() {
        if  (LOGGER_FACTORY == null)
            throw new NullPointerException("LoggerFactory was not initialized");
        return LOGGER_FACTORY;
    }

    public static Logger getLogger() {
        if  (LOGGER == null)
            throw new NullPointerException("Logger was not initialized");
        return LOGGER;
    }
}
