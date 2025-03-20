package top.gregtao.concerto.enums;

import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import top.gregtao.concerto.api.SimpleStringIdentifiable;

public enum Sources implements SimpleStringIdentifiable {
    LOCAL_FILE,
    INTERNET,
    NETEASE_CLOUD,
    QQ_MUSIC,
    BILIBILI,
    SHARED
    ;

    public Text getName() {
        return new TranslatableText(this.getKey("source"));
    }

    public String getKey(String main) {
        return "concerto." + main + "." + this.asString();
    }
}