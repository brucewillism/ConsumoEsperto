package com.consumoesperto.autonomy;

import java.time.LocalTime;

public final class QuietHours {

    private QuietHours() {}

    public static boolean active(LocalTime now, LocalTime start, LocalTime end) {
        if (now == null || start == null || end == null) {
            return false;
        }
        if (start.equals(end)) {
            return false;
        }
        if (start.isBefore(end)) {
            return !now.isBefore(start) && now.isBefore(end);
        }
        return !now.isBefore(start) || now.isBefore(end);
    }
}
