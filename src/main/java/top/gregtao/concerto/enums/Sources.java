package top.gregtao.concerto.enums;

import net.minecraft.text.Text;
import top.gregtao.concerto.api.SimpleStringIdentifiable;

public enum Sources implements SimpleStringIdentifiable {
    LOCAL_FILE,
    INTERNET,
    NETEASE_CLOUD,
    QQ_MUSIC;

    public Text getName() {
        return Text.translatable(this.getKey("source"));
    }

    public String getKey(String main) {
        return "concerto." + main + "." + this.asString();
    }
}