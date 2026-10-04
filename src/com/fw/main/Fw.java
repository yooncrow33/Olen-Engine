package com.fw.main;

import com.fw.internal.utils.Internal;
import com.fw.main.utils.collision.Hitbox;
import com.fw.main.utils.platform.system.console.Console;

import java.awt.*;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class Fw {
    private static Map<Integer, Base> integerBaseMap = new ConcurrentHashMap<>();
    private static Map<String, Base> stringBaseMap = new ConcurrentHashMap<>();
    public static void add(String key,Base base) {
        stringBaseMap.put(key,base); }
    public static void add(int integer, Base base) {
        integerBaseMap.put(integer,base); }
    public static Base get(String key) {return stringBaseMap.get(key);}
    public static Base get(int key) {return integerBaseMap.get(key);}

    public static class Debugger {
        public static boolean showHitbox;
        public static boolean atlasDebugger;
        private static Map<UUID, Hitbox> hitboxMap = new ConcurrentHashMap<>();
        private static Map<UUID, Hitbox> activeHitboxMap = new ConcurrentHashMap<>();
        static void renderHitbox(Graphics g) {
            for (Hitbox h : activeHitboxMap.values()) {
                h.renderHitbox(g);
            }
        }
        static void addObject(Hitbox hitbox) {
            hitboxMap.put(hitbox.id,hitbox);
        }
        static void enableObject(Hitbox hitbox) {
            activeHitboxMap.put(hitbox.id,hitbox);
        }
        static void freeObject(Hitbox hitbox) {
            hitboxMap.remove(hitbox.id);
        }
        static void disableObject(Hitbox hitbox) {
            activeHitboxMap.remove(hitbox.id);
        }
        @com.fw.internal.utils.Internal
        public static class Internal {
            public static void addObject(Hitbox hitbox) {
                Debugger.addObject(hitbox);
            }
            public static void freeObject(Hitbox hitbox) {
                Debugger.freeObject(hitbox);
            }
            public static void enableObject(Hitbox hitbox) { Debugger.enableObject(hitbox); }
            public static void disableObject(Hitbox hitbox) { Debugger.disableObject(hitbox); }
            public static void renderHitbox(Graphics g) {
                Debugger.renderHitbox(g);
            }
        }
    }

    @Internal
    /**
     * Danger and legacy.
     */
    @Deprecated
    public static class Helper {
        public static Console getConsoleToBaseInstance(Base base) {
            return base.console;
        }
    }
}
