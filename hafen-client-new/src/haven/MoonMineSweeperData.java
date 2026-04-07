package haven;

import java.io.IOException;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.Toolkit;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Session-scoped local mine-sweeper memory.
 * Marks live only in RAM unless the user explicitly saves/loads a session snapshot.
 */
public final class MoonMineSweeperData {
    private MoonMineSweeperData() {}

    public static final byte UNKNOWN = 0;
    public static final byte SAFE = 1;
    public static final byte RISK = 2;
    public static final byte OPEN = 3;
    public static final byte AUTO_SAFE = 4;
    public static final int NO_CLUE = -1;

    private static final Object lock = new Object();
    private static final Map<Long, Byte> marks = new HashMap<>();
    private static final Map<Long, Byte> clues = new HashMap<>();
    private static boolean loaded = false;
    private static final Path storePath = Paths.get(System.getProperty("user.home", "."), ".haven", "moon-mine-sweeper-map.tsv");
    private static final Path sessionPath = Paths.get(System.getProperty("user.home", "."), ".haven", "moon-mine-sweeper-session.tsv");

    private static long key(Coord tc) {
        return (((long)tc.x) << 32) ^ (tc.y & 0xffffffffL);
    }

    private static Coord unpack(long key) {
        return Coord.of((int)(key >> 32), (int)key);
    }

    private static void ensureLoaded() {
        synchronized(lock) {
            if(loaded)
                return;
            loaded = true;
            try {
                Files.deleteIfExists(storePath);
            } catch(Exception ignored) {
            }
        }
    }

    private static void saveLocked() {
        try {
            Files.deleteIfExists(storePath);
        } catch(IOException ignored) {
        }
    }

    public static byte state(Coord tc) {
        ensureLoaded();
        synchronized(lock) {
            Byte st = marks.get(key(tc));
            return(st == null) ? UNKNOWN : st;
        }
    }

    public static int clue(Coord tc) {
        ensureLoaded();
        synchronized(lock) {
            Byte clue = clues.get(key(tc));
            return(clue == null) ? NO_CLUE : clue.intValue();
        }
    }

    public static void mark(Coord tc, byte state) {
        if(tc == null || ((state != SAFE) && (state != RISK) && (state != AUTO_SAFE)))
            return;
        ensureLoaded();
        synchronized(lock) {
            byte normalized = normalizeManualState(state);
            setStateLocked(tc, normalized);
            if(normalized == RISK)
                clues.remove(key(tc));
            propagateLocked();
            saveLocked();
        }
    }

    public static void markArea(Area a, byte state) {
        if(a == null || !a.positive() || ((state != SAFE) && (state != RISK) && (state != AUTO_SAFE)))
            return;
        ensureLoaded();
        synchronized(lock) {
            byte normalized = normalizeManualState(state);
            for(int y = a.ul.y; y < a.br.y; y++) {
                for(int x = a.ul.x; x < a.br.x; x++) {
                    Coord tc = Coord.of(x, y);
                    setStateLocked(tc, normalized);
                    if(normalized == RISK)
                        clues.remove(key(tc));
                }
            }
            propagateLocked();
            saveLocked();
        }
    }

    public static void resetRiskMarks() {
        ensureLoaded();
        synchronized(lock) {
            java.util.List<Long> drop = new ArrayList<>();
            for(Map.Entry<Long, Byte> e : marks.entrySet()) {
                if(e.getValue() == RISK)
                    drop.add(e.getKey());
            }
            if(drop.isEmpty())
                return;
            for(Long k : drop)
                marks.remove(k);
            propagateLocked();
            saveLocked();
        }
    }

    public static void clearAll() {
        ensureLoaded();
        synchronized(lock) {
            marks.clear();
            clues.clear();
            saveLocked();
        }
    }

    public static int saveSessionSnapshot() {
        ensureLoaded();
        synchronized(lock) {
            return writeSnapshotLocked(sessionPath);
        }
    }

    public static int loadSessionSnapshot() {
        ensureLoaded();
        synchronized(lock) {
            if(!Files.isRegularFile(sessionPath))
                return -1;
            try {
                int n = decodeIntoLocked(Files.readString(sessionPath, StandardCharsets.UTF_8), true);
                saveLocked();
                return n;
            } catch(Exception e) {
                return -1;
            }
        }
    }

    public static String exportSnapshot() {
        ensureLoaded();
        synchronized(lock) {
            return encodeLocked();
        }
    }

    public static int importSnapshotReplace(String text) {
        ensureLoaded();
        synchronized(lock) {
            try {
                int n = decodeIntoLocked(text, true);
                saveLocked();
                return n;
            } catch(Exception e) {
                return -1;
            }
        }
    }

    public static boolean exportSnapshotToClipboard() {
        try {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(exportSnapshot()), null);
            return true;
        } catch(Exception e) {
            return false;
        }
    }

    public static int importSnapshotFromClipboard() {
        try {
            Object data = Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.stringFlavor);
            return importSnapshotReplace((data == null) ? "" : data.toString());
        } catch(Exception e) {
            return -1;
        }
    }

    public static void observeClue(Coord tc, int clue) {
        if(tc == null || clue < 0 || clue > 8)
            return;
        ensureLoaded();
        synchronized(lock) {
            long k = key(tc);
            byte prevState = marks.getOrDefault(k, UNKNOWN);
            int prevClue = clues.getOrDefault(k, (byte)NO_CLUE);
            byte nextState = (clue == 0) ? SAFE : OPEN;
            boolean changed = (prevState != nextState) || (prevClue != clue);
            marks.put(k, nextState);
            clues.put(k, (byte)clue);
            if(clue == 0)
                markNeighborsLocked(tc, AUTO_SAFE);
            if(propagateLocked() || changed)
                saveLocked();
        }
    }

    public static int riskCountAround(Coord tc) {
        ensureLoaded();
        int n = 0;
        synchronized(lock) {
            for(int dy = -1; dy <= 1; dy++) {
                for(int dx = -1; dx <= 1; dx++) {
                    if((dx == 0) && (dy == 0))
                        continue;
                    Byte st = marks.get(key(tc.add(dx, dy)));
                    if(st != null && st == RISK)
                        n++;
                }
            }
        }
        return n;
    }

    public static Map<Coord, Byte> snapshot(Area area) {
        ensureLoaded();
        Map<Coord, Byte> out = new HashMap<>();
        synchronized(lock) {
            for(Map.Entry<Long, Byte> e : marks.entrySet()) {
                Coord tc = unpack(e.getKey());
                if(area != null && !area.contains(tc))
                    continue;
                out.put(tc, e.getValue());
            }
        }
        return out;
    }

    public static Map<Coord, Integer> clueSnapshot(Area area) {
        ensureLoaded();
        Map<Coord, Integer> out = new HashMap<>();
        synchronized(lock) {
            for(Map.Entry<Long, Byte> e : clues.entrySet()) {
                Coord tc = unpack(e.getKey());
                if(area != null && !area.contains(tc))
                    continue;
                out.put(tc, (int)e.getValue());
            }
        }
        return out;
    }

    public static List<Coord> allMarkedTiles() {
        ensureLoaded();
        List<Coord> out = new ArrayList<>();
        synchronized(lock) {
            for(Long k : marks.keySet())
                out.add(unpack(k));
        }
        return out;
    }

    private static boolean setStateLocked(Coord tc, byte state) {
        long k = key(tc);
        byte prev = marks.getOrDefault(k, UNKNOWN);
        if(prev == state)
            return false;
        if(prev == SAFE && state == AUTO_SAFE)
            return false;
        if(prev == RISK && state != RISK)
            return false;
        marks.put(k, state);
        return true;
    }

    private static void markNeighborsLocked(Coord tc, byte state) {
        for(int dy = -1; dy <= 1; dy++) {
            for(int dx = -1; dx <= 1; dx++) {
                if(dx == 0 && dy == 0)
                    continue;
                Coord nc = tc.add(dx, dy);
                if(state == SAFE || state == AUTO_SAFE) {
                    byte cur = marks.getOrDefault(key(nc), UNKNOWN);
                    if(cur == UNKNOWN)
                        marks.put(key(nc), AUTO_SAFE);
                } else {
                    setStateLocked(nc, state);
                }
            }
        }
    }

    private static boolean propagateLocked() {
        boolean any = false;
        boolean changed;
        do {
            changed = false;
            for(Map.Entry<Long, Byte> e : clues.entrySet()) {
                Coord tc = unpack(e.getKey());
                int clue = e.getValue();
                int knownRisk = 0;
                java.util.List<Coord> unknown = new ArrayList<>();
                for(int dy = -1; dy <= 1; dy++) {
                    for(int dx = -1; dx <= 1; dx++) {
                        if(dx == 0 && dy == 0)
                            continue;
                        Coord nc = tc.add(dx, dy);
                        byte st = marks.getOrDefault(key(nc), UNKNOWN);
                        if(st == RISK)
                            knownRisk++;
                        else if(st == UNKNOWN)
                            unknown.add(nc);
                    }
                }
                if(unknown.isEmpty())
                    continue;
                if(knownRisk >= clue) {
                    for(Coord uc : unknown)
                        changed |= setStateLocked(uc, AUTO_SAFE);
                } else if(knownRisk + unknown.size() == clue) {
                    for(Coord uc : unknown)
                        changed |= setStateLocked(uc, RISK);
                }
            }
            any |= changed;
        } while(changed);
        return any;
    }

    private static byte normalizeManualState(byte state) {
        return(state == SAFE) ? SAFE : state;
    }

    private static String encodeLocked() {
        java.util.Set<Long> keys = new java.util.HashSet<>(marks.keySet());
        keys.addAll(clues.keySet());
        StringBuilder buf = new StringBuilder(Math.max(64, keys.size() * 20));
        for(Long k : keys) {
            byte st = marks.getOrDefault(k, UNKNOWN);
            byte clue = clues.getOrDefault(k, (byte)NO_CLUE);
            if(st == UNKNOWN && clue == NO_CLUE)
                continue;
            Coord tc = unpack(k);
            buf.append(tc.x).append(' ').append(tc.y).append(' ').append(st).append(' ').append((int)clue).append('\n');
        }
        return buf.toString();
    }

    private static int writeSnapshotLocked(Path path) {
        try {
            Path parent = path.getParent();
            if(parent != null)
                Files.createDirectories(parent);
            Files.write(path, encodeLocked().getBytes(StandardCharsets.UTF_8));
            return marks.size() + clues.size();
        } catch(IOException e) {
            return -1;
        }
    }

    private static int decodeIntoLocked(String text, boolean replace) {
        if(replace) {
            marks.clear();
            clues.clear();
        }
        int n = 0;
        if(text == null)
            return 0;
        for(String ln : text.split("\\R")) {
            if(ln == null)
                continue;
            String s = ln.trim();
            if(s.isEmpty() || s.startsWith("#"))
                continue;
            String[] parts = s.split("\\s+");
            if(parts.length < 3)
                continue;
            int x = Integer.parseInt(parts[0]);
            int y = Integer.parseInt(parts[1]);
            byte st = Byte.parseByte(parts[2]);
            long k = key(Coord.of(x, y));
            if((st == SAFE) || (st == RISK) || (st == OPEN) || (st == AUTO_SAFE)) {
                marks.put(k, st);
                n++;
            }
            if(parts.length >= 4) {
                int clue = Integer.parseInt(parts[3]);
                if(clue >= 0 && clue <= 8)
                    clues.put(k, (byte)clue);
            }
        }
        return n;
    }
}
