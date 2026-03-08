package top.gregtao.concerto.core;

import top.gregtao.concerto.core.bridge.Logger;
import top.gregtao.concerto.core.bridge.LoggerFactory;
import top.gregtao.concerto.core.bridge.Minecraft;
import top.gregtao.concerto.core.config.ConfigFile;

public class Concerto {

    public static final String MOD_ID = "concerto";

    private static Minecraft MINECRAFT;
    private static Logger LOGGER;
    private static LoggerFactory LOGGER_FACTORY;

    public static final ConfigFile MUSIC_CONFIG = new ConfigFile("Concerto/musics.json");

    public static void registerMinecraft(Minecraft mc, LoggerFactory loggerFactory) {
        MINECRAFT = mc;
        LOGGER_FACTORY = loggerFactory;
        LOGGER = loggerFactory.getLogger("Concerto");
    }

    public static Minecraft getMinecraft() {
        if (MINECRAFT == null)
            throw new NullPointerException("Minecraft was not initialized");
        return MINECRAFT;
    }

    public static LoggerFactory getLoggerFactory() {
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
