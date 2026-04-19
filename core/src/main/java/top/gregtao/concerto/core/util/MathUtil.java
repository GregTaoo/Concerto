package top.gregtao.concerto.core.util;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;

public class MathUtil {

    public static int parseIntOrElse(String nm, int orElse) {
        try {
            return Integer.parseInt(nm);
        } catch (NumberFormatException e) {
            return orElse;
        }
    }

    public static String formattedTime(long timestamp) {
        Date date = new Date(timestamp);
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss");
        return dateFormat.format(date);
    }

    public static String formattedTime(int timestamp) {
        return formattedTime((long) timestamp);
    }

    public static String formattedTime(String timestamp) {
        return formattedTime(parseIntOrElse(timestamp, 0));
    }

    public static int clamp(int value, int min, int max) {
        return Math.min(Math.max(value, min), max);
    }

    public static <T> int lowerBound(ArrayList<T> a, T key, Comparator<? super T> c) {
        return lowerBound(a, 0, a.size() - 1, key, c);
    }

    public static <T> int lowerBound(ArrayList<T> a, int low, int high, T key, Comparator<? super T> c) {
        while (low < high) {
            int mid = (low + high) >>> 1;
            T midVal = a.get(mid);
            int cmp = c.compare(midVal, key);
            if (cmp < 0) low = mid + 1; // midVal < key
            else high = mid;
        }
        return low;
    }

    public static <T> int upperBound(ArrayList<T> a, T key, Comparator<? super T> c) {
        return upperBound(a, 0, a.size() - 1, key, c);
    }

    public static <T> int upperBound(ArrayList<T> a, int low, int high, T key, Comparator<? super T> c) {
        while (low < high) {
            int mid = (low + high) >>> 1;
            T midVal = a.get(mid);
            int cmp = c.compare(midVal, key);
            if (cmp <= 0) low = mid + 1; // midVal <= key
            else high = mid;
        }
        return low;
    }
}
