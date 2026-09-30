package server;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Thread-safe map of file ids to file names, persisted to disk after every change
 * so ids survive a server restart and are never reused.
 *
 * File format: first line is the next id to hand out, then one "id name" pair per line.
 */
class FileIndex {

    private final Path indexFile;
    private final Path dataDir;
    private final Map<Integer, String> idToName = new HashMap<>();
    private final Map<String, Integer> nameToId = new HashMap<>();
    private int nextId = 1;

    FileIndex(Path indexFile, Path dataDir) throws IOException {
        this.indexFile = indexFile;
        this.dataDir = dataDir;
        load();
    }

    synchronized String nameOf(int id) {
        return idToName.get(id);
    }

    synchronized Integer idOf(String name) {
        return nameToId.get(name);
    }

    /** Registers a name and returns its new id, or -1 if the name is already taken. */
    synchronized int add(String name) {
        if (nameToId.containsKey(name)) {
            if (Files.exists(dataDir.resolve(name))) {
                return -1;
            }
            // file was removed behind our back; drop the stale entry
            remove(name);
        }
        int id = nextId++;
        idToName.put(id, name);
        nameToId.put(name, id);
        save();
        return id;
    }

    /** Reserves a fresh id and a server-generated name that isn't used yet. */
    synchronized int addGenerated() {
        String name;
        int suffix = nextId;
        do {
            name = "file_" + suffix++;
        } while (nameToId.containsKey(name) || Files.exists(dataDir.resolve(name)));
        return add(name);
    }

    synchronized void remove(String name) {
        Integer id = nameToId.remove(name);
        if (id != null) {
            idToName.remove(id);
            save();
        }
    }

    private void load() throws IOException {
        if (!Files.exists(indexFile)) {
            return;
        }
        List<String> lines = Files.readAllLines(indexFile, StandardCharsets.UTF_8);
        if (lines.isEmpty()) {
            return;
        }
        nextId = Integer.parseInt(lines.get(0).trim());
        for (String line : lines.subList(1, lines.size())) {
            String[] parts = line.split(" ", 2);
            if (parts.length != 2) {
                continue;
            }
            int id = Integer.parseInt(parts[0]);
            // skip entries whose files no longer exist
            if (Files.exists(dataDir.resolve(parts[1]))) {
                idToName.put(id, parts[1]);
                nameToId.put(parts[1], id);
            }
        }
    }

    private void save() {
        List<String> lines = new ArrayList<>();
        lines.add(String.valueOf(nextId));
        idToName.forEach((id, name) -> lines.add(id + " " + name));
        try {
            Path tmp = indexFile.resolveSibling(indexFile.getFileName() + ".tmp");
            Files.write(tmp, lines, StandardCharsets.UTF_8);
            Files.move(tmp, indexFile, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
