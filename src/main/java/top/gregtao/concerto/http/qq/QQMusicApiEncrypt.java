package top.gregtao.concerto.http.qq;

import top.gregtao.concerto.util.HashUtil;

import java.util.*;

public class QQMusicApiEncrypt {

    //    // Source by: https://blog.csdn.net/qq_23594799/article/details/111477320, https://blog.csdn.net/qq_37438485/article/details/124420854
    //
    //    private static final String ENCRYPT_STATIC = "CJBPACrRuNy7";
    //    private static final String PREFIX = "zzb";
    //
    //    /**
    //     * @param body 需要加密的参数，这是一段请求体数据，为json字符串格式，例如下面的格式，可以抓包获取
    //     *                 {"comm":{"ct":24,"cv":0},"vip":{"module":"userInfo…baseinfo_v2","param":{"vec_uin":["3011429848"]}}}
    //     * @return 加密的方式为固定字串 zza + 10-16位的随机字符串 + (CJBPACrRuNy7 + 请求数据)的MD5值
    //     */
    //    public static String getSign(String body){
    //        return PREFIX + UUID.randomUUID().toString().replaceAll("-", "") + HashUtil.md5(ENCRYPT_STATIC + body);
    //    }
    public static class Sign {

        private static void test(List<Integer> resNum, int a, int b, int c) {
            int r25 = a >> 2;
            if (b != -1 && c != -1) {
                int r26 = a & 3;
                int r26_2 = r26 << 4;
                int r26_3 = b >> 4;
                int r26_4 = r26_2 | r26_3;
                int r27 = b & 15;
                int r27_2 = r27 << 2;
                int r27_3 = r27_2 | (c >> 6);
                int r28 = c & 63;
                resNum.add(r25);
                resNum.add(r26_4);
                resNum.add(r27_3);
                resNum.add(r28);
            } else {
                int r10 = a >> 2;
                int r11 = a & 3;
                int r11_2 = r11 << 4;
                resNum.add(r10);
                resNum.add(r11_2);
            }
        }

        private static String middle(List<Integer> ls) {
            final List<Integer> resNum = new ArrayList<>();
            final List<String> res = new ArrayList<>();
            for (int i = 0; i < ls.size(); i += 3) {
                if (ls.get(i) != null && (i + 1 < ls.size() && ls.get(i + 1) != null) && (i + 2 < ls.size() && ls.get(i + 2) != null)) {
                    test(resNum, ls.get(i), ls.get(i + 1), ls.get(i + 2));
                } else {
                    test(resNum, ls.get(i), -1, -1);
                }
            }
            char[] zd = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/=".toCharArray();
            resNum.forEach((item) -> res.add(String.valueOf(zd[item])));
            return String.join("", res);
        }

        private static String head(String md5Str) {
            final List<String> res = new ArrayList<>();
            char[] chars = md5Str.toCharArray();
            List.of(21, 4, 9, 26, 16, 20, 27, 30).forEach(integer -> {
                res.add(String.valueOf(chars[integer]));
            });
            return String.join("", res);
        }

        private static String tail(String md5Str) {
            final List<String> res = new ArrayList<>();
            char[] chars = md5Str.toCharArray();
            List.of(18, 11, 3, 2, 1, 7, 6, 25).forEach(integer -> {
                res.add(String.valueOf(chars[integer]));
            });
            return String.join("", res);
        }

        private static List<Integer> getLs(String md5Str) {
            Map<Character, Integer> zd = new HashMap<>(Map.of(
                    '0', 0, '1', 1, '2', 2, '3', 3, '4', 4,
                    '5', 5, '6', 6, '7', 7, '8', 8, '9', 9
            ));
            zd.putAll(Map.of(
                    'A', 10, 'B', 11, 'C', 12, 'D', 13, 'E', 14, 'F', 15
            ));
            List<Integer> ol = List.of(212, 45, 80, 68, 195, 163, 163, 203, 157, 220, 254, 91, 204, 79, 104, 6);
            List<Integer> res = new ArrayList<>();
            int j = 0;
            char[] chars = md5Str.toCharArray();
            for (int i = 0; i < md5Str.length(); i += 2) {
                int one = zd.get(chars[i]);
                int two = zd.get(chars[i + 1]);
                int r = one * 16 ^ two;
                res.add(r ^ ol.get(j));
                j += 1;
            }
            return res;
        }

        public static String getSign(String params) {
            String md5Str = HashUtil.md5(params).toUpperCase();
            String h = head(md5Str), e = tail(md5Str);
            List<Integer> ls = getLs(md5Str);
            String m = middle(ls), res = ("zzb" + h + m + e).toLowerCase();
            res = res.replaceAll("[/+]", "");
            return res;
        }
    }
}
