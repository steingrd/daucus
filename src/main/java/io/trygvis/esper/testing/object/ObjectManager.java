package io.trygvis.esper.testing.object;

import java.io.*;
import java.util.*;

public class ObjectManager<K, V extends Closeable> implements Closeable {
    private final String type;
    private final ObjectFactory<K, V> objectFactory;
    private Map<K, V> objects = new HashMap<>();
    private boolean closed = false;

    public ObjectManager(String type, Set<K> initialKeys, ObjectFactory<K, V> objectFactory) {
        this.type = type;
        this.objectFactory = objectFactory;

        update(initialKeys);
    }

    public synchronized void update(Collection<K> newKeys) {
        if (closed) {
            throw new RuntimeException("This instance is closed: type=" + type);
        }
        Set<K> found = new HashSet<>(newKeys);
        found.removeAll(objects.keySet());

        Set<K> gone = new HashSet<>(objects.keySet());
        gone.removeAll(newKeys);

        for (K k : gone) {
            try {
                System.out.println("Removing " + type + " with id=" + k);
                objects.remove(k).close();
            } catch (IOException e) {
                e.printStackTrace(System.out);
            }
        }

        for (K k : found) {
            System.out.println("Adding " + type + " with id=" + k);
            objects.put(k, objectFactory.create(k));
        }
    }

    public synchronized void close() throws IOException {
        if (closed) {
            System.out.println("Already closed: type=" + type);
            return;
        }
        update(Collections.<K>emptyList());
        closed = true;
    }

    public synchronized Collection<V> getObjects() {
        return new ArrayList<>(objects.values());
    }

    public void setObjects(Map<K, V> objects) {
        this.objects = objects;
    }
}
