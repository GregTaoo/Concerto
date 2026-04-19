package top.gregtao.concerto.core.player;

import com.google.gson.*;
import top.gregtao.concerto.core.api.Copyable;
import top.gregtao.concerto.core.api.MusicJsonParsers;
import top.gregtao.concerto.core.music.Music;

import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiConsumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import top.gregtao.concerto.core.util.Pair;

public class ConcertoPlayerList implements Copyable<ConcertoPlayerList> {

    private static final class Node {
        UUID uuid;
        Music music;
        Node prev;
        Node next;

        Node(UUID uuid, Music music) {
            this.uuid = Objects.requireNonNull(uuid, "uuid");
            this.music = Objects.requireNonNull(music, "music");
        }
    }

    private Node head;
    private Node tail;
    private int size = 0;

    private final HashMap<UUID, Node> byUuid = new HashMap<>();
    private final ArrayList<UUID> randomUuids = new ArrayList<>();
    private final HashMap<UUID, Integer> randomPos = new HashMap<>();

    public ConcertoPlayerList() {}

    public ConcertoPlayerList(Collection<Music> musics) {
        this.addAllLast(musics);
    }

    public int size() {
        return size;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    public boolean contains(UUID uuid) {
        return uuid != null && byUuid.containsKey(uuid);
    }

    public void clear() {
        this.head = this.tail = null;
        this.size = 0;
        this.byUuid.clear();
        this.randomUuids.clear();
        this.randomPos.clear();
    }

    public UUID firstUuid() {
        return head == null ? null : head.uuid;
    }

    public UUID lastUuid() {
        return tail == null ? null : tail.uuid;
    }

    public UUID nextUuid(UUID uuid) {
        Node n = byUuid.get(uuid);
        return (n == null || n.next == null) ? null : n.next.uuid;
    }

    public UUID previousUuid(UUID uuid) {
        Node n = byUuid.get(uuid);
        return (n == null || n.prev == null) ? null : n.prev.uuid;
    }

    public int indexOf(UUID uuid) {
        if (uuid == null) return -1;
        int i = 0;
        Node cur = head;
        while (cur != null) {
            if (cur.uuid.equals(uuid)) return i;
            cur = cur.next;
            i++;
        }
        return -1;
    }

    public UUID uuidAt(int index) {
        if (index < 0 || index >= size) return null;
        int i = 0;
        Node cur = head;
        while (cur != null) {
            if (i == index) return cur.uuid;
            cur = cur.next;
            i++;
        }
        return null;
    }

    public ArrayList<Music> snapshotMusics() {
        ArrayList<Music> out = new ArrayList<>(size);
        Node cur = head;
        while (cur != null) {
            out.add(cur.music);
            cur = cur.next;
        }
        return out;
    }

    public void forEach(BiConsumer<UUID, Music> action) {
        Node cur = head;
        while (cur != null) {
            action.accept(cur.uuid, cur.music);
            cur = cur.next;
        }
    }

    public Stream<Pair<UUID, Music>> stream() {
        Iterator<Pair<UUID, Music>> iterator = new Iterator<>() {
            private Node current = head;

            @Override
            public boolean hasNext() {
                return current != null;
            }

            @Override
            public Pair<UUID, Music> next() {
                if (current == null) throw new NoSuchElementException();
                Pair<UUID, Music> p = new Pair<>(current.uuid, current.music);
                current = current.next;
                return p;
            }
        };
        return StreamSupport.stream(Spliterators.spliterator(iterator, size, Spliterator.ORDERED | Spliterator.NONNULL | Spliterator.SIZED), false);
    }

    public UUID addFirst(Music music) {
        Node n = new Node(UUID.randomUUID(), music);
        if (head == null) {
            head = tail = n;
        } else {
            n.next = head;
            head.prev = n;
            head = n;
        }
        onNodeAdded(n);
        return n.uuid;
    }

    public UUID addLast(Music music) {
        Node n = new Node(UUID.randomUUID(), music);
        if (tail == null) {
            head = tail = n;
        } else {
            tail.next = n;
            n.prev = tail;
            tail = n;
        }
        onNodeAdded(n);
        return n.uuid;
    }

    public List<UUID> addAfter(UUID anchorUuid, Collection<Music> musics) {
        Objects.requireNonNull(musics, "musics");
        Node anchor = byUuid.get(anchorUuid);
        if (anchor == null || musics.isEmpty()) return Collections.emptyList();

        ArrayList<UUID> ids = new ArrayList<>(musics.size());
        Node cursor = anchor;
        for (Music m : musics) {
            Node n = new Node(UUID.randomUUID(), m);

            Node oldNext = cursor.next;
            cursor.next = n;
            n.prev = cursor;
            n.next = oldNext;
            if (oldNext != null) oldNext.prev = n;
            else tail = n;

            onNodeAdded(n);
            ids.add(n.uuid);
            cursor = n;
        }
        return ids;
    }

    public List<UUID> addBefore(UUID anchorUuid, Collection<Music> musics) {
        Objects.requireNonNull(musics, "musics");
        Node anchor = byUuid.get(anchorUuid);
        if (anchor == null || musics.isEmpty()) return Collections.emptyList();

        ArrayList<UUID> ids = new ArrayList<>(musics.size());
        Node cursor = anchor.prev;

        for (Music m : musics) {
            Node n = new Node(UUID.randomUUID(), m);

            if (cursor == null) {
                n.next = head;
                head.prev = n;
                head = n;
            } else {
                Node oldNext = cursor.next;
                cursor.next = n;
                n.prev = cursor;
                n.next = oldNext;
                oldNext.prev = n;
            }

            onNodeAdded(n);
            ids.add(n.uuid);
            cursor = n;
        }
        return ids;
    }

    public List<UUID> addAllFirst(Collection<Music> musics) {
        Objects.requireNonNull(musics, "musics");
        if (musics.isEmpty()) return Collections.emptyList();

        ArrayList<UUID> ids = new ArrayList<>(musics.size());
        List<Music> list = (musics instanceof List) ? (List<Music>) musics : new ArrayList<>(musics);

        for (int i = list.size() - 1; i >= 0; i--) {
            ids.add(0, addFirst(list.get(i)));
        }
        return ids;
    }

    public List<UUID> addAllLast(Collection<Music> musics) {
        Objects.requireNonNull(musics, "musics");
        if (musics.isEmpty()) return Collections.emptyList();

        ArrayList<UUID> ids = new ArrayList<>(musics.size());
        for (Music m : musics) ids.add(addLast(m));
        return ids;
    }

    public boolean remove(UUID uuid) {
        Node n = byUuid.get(uuid);
        if (n == null) return false;
        unlink(n);
        return true;
    }

    public UUID removeFirst() {
        if (head == null) return null;
        UUID id = head.uuid;
        unlink(head);
        return id;
    }

    public UUID removeLast() {
        if (tail == null) return null;
        UUID id = tail.uuid;
        unlink(tail);
        return id;
    }

    public UUID randomUuid() {
        if (size == 0) return null;
        int idx = ThreadLocalRandom.current().nextInt(randomUuids.size());
        return randomUuids.get(idx);
    }

    public ConcertoPlayerList copy() {
        ConcertoPlayerList c = new ConcertoPlayerList();
        Node cur = this.head;
        while (cur != null) {
            c.appendExistingUuid(cur.uuid, cur.music);
            cur = cur.next;
        }
        return c;
    }

    public static final class GsonAdapter implements JsonSerializer<ConcertoPlayerList>, JsonDeserializer<ConcertoPlayerList> {

        @Override
        public JsonElement serialize(ConcertoPlayerList src, Type typeOfSrc, JsonSerializationContext context) {
            JsonArray items = new JsonArray();

            Node cur = src.head;
            while (cur != null) {
                JsonObject o = new JsonObject();
                o.addProperty("uuid", cur.uuid.toString());
                o.add("music", MusicJsonParsers.to(cur.music));
                items.add(o);
                cur = cur.next;
            }
            return items;
        }

        @Override
        public ConcertoPlayerList deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
                throws JsonParseException {
            JsonArray items = json.getAsJsonArray();

            ConcertoPlayerList list = new ConcertoPlayerList();
            if (items == null) return list;

            for (JsonElement e : items) {
                JsonObject o = e.getAsJsonObject();
                UUID uuid = UUID.fromString(o.get("uuid").getAsString());
                Music music = MusicJsonParsers.from(o.getAsJsonObject("music"));
                list.appendExistingUuid(uuid, music);
            }
            return list;
        }
    }

    public Music get(UUID uuid) {
        Node n = byUuid.get(uuid);
        return n == null ? null : n.music;
    }

    private void onNodeAdded(Node n) {
        byUuid.put(n.uuid, n);

        randomPos.put(n.uuid, randomUuids.size());
        randomUuids.add(n.uuid);

        size++;
    }

    private void unlink(Node n) {
        Node p = n.prev, x = n.next;

        if (p == null) head = x;
        else p.next = x;

        if (x == null) tail = p;
        else x.prev = p;

        byUuid.remove(n.uuid);
        removeFromRandomIndex(n.uuid);

        n.prev = n.next = null;
        size--;
    }

    private void removeFromRandomIndex(UUID uuid) {
        Integer idxObj = randomPos.remove(uuid);
        if (idxObj == null) return;

        int idx = idxObj;
        int last = randomUuids.size() - 1;
        UUID lastUuid = randomUuids.get(last);

        if (idx != last) {
            randomUuids.set(idx, lastUuid);
            randomPos.put(lastUuid, idx);
        }
        randomUuids.remove(last);
    }

    private void appendExistingUuid(UUID uuid, Music music) {
        if (byUuid.containsKey(uuid)) {
            throw new IllegalArgumentException("duplicate uuid: " + uuid);
        }

        Node n = new Node(uuid, music);
        if (tail == null) {
            head = tail = n;
        } else {
            tail.next = n;
            n.prev = tail;
            tail = n;
        }
        onNodeAdded(n);
    }
}
