package top.gregtao.concerto.core.enums;

import top.gregtao.concerto.core.Concerto;
import top.gregtao.concerto.core.api.SimpleStringIdentifiable;

public enum Sources implements SimpleStringIdentifiable {
    LOCAL_FILE,
    INTERNET,
    NETEASE_CLOUD,
    QQ_MUSIC,
    KUGOU_MUSIC,
    BILIBILI,
    SHARED
    ;

    public String getName() {
        return Concerto.getCoreBridge().getTranslatable(this.getKey("source"));
    }

    public String getKey(String main) {
        return "concerto." + main + "." + this.asString();
    }

    public static String getI18nString(String source) {
        return Concerto.getCoreBridge().getTranslatable("concerto.source." + source);
    }
}