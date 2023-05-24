package top.gregtao.concerto.http.encrypt;

import top.gregtao.concerto.util.HashUtil;

import java.util.UUID;

public class QQMusicApiEncrypt {

    // Source by: https://blog.csdn.net/qq_23594799/article/details/111477320

    private static final String ENCRYPT_STATIC = "CJBPACrRuNy7";
    private static final String PREFIX = "zza";

    /**
     * @param body 需要加密的参数，这是一段请求体数据，为json字符串格式，例如下面的格式，可以抓包获取
     *                 {"comm":{"ct":24,"cv":0},"vip":{"module":"userInfo…baseinfo_v2","param":{"vec_uin":["3011429848"]}}}
     * @return 加密的方式为固定字串 zza + 10-16位的随机字符串 + (CJBPACrRuNy7 + 请求数据)的MD5值
     */
    public static String getSign(String body){
        return PREFIX + UUID.randomUUID().toString().replaceAll("-", "") + HashUtil.md5(ENCRYPT_STATIC + body);
    }
}
