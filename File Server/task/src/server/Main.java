package server;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class Main {

    private static final String ADDRESS = "127.0.0.1";
    private static final int PORT = 23456;
    //sever folder path
    private static final Path SERVER_DIR = Paths.get(System.getProperty("user.dir"), "src", "server");
    //data folder path
    private static final Path DATA_DIR = SERVER_DIR.resolve("data");
    // kept outside data/ so it isn't mistaken for a stored file
    private static final Path INDEX_FILE = SERVER_DIR.resolve("index.txt");

    private static FileIndex index;
    private static ServerSocket server;

    public static void main(String[] args) throws Exception {
        //Creates a directory by creating all nonexistent parent directories first.
        Files.createDirectories(DATA_DIR);
        index = new FileIndex(INDEX_FILE, DATA_DIR);

        ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        try (ServerSocket serverSocket = new ServerSocket(PORT, 50, InetAddress.getByName(ADDRESS))) {
            server = serverSocket;
            System.out.println("Server started!");
            while (true) {
                Socket socket;
                try {
                    socket = serverSocket.accept();
                } catch (SocketException e) {
                    //the socket was closed by an "exit" request
                    break;
                }
                executor.submit(() -> serve(socket));
            }
        } finally {
            executor.shutdown();
            executor.awaitTermination(10, TimeUnit.SECONDS);
        }
    }

    private static void serve(Socket socket) {
        try (socket;
             DataInputStream input = new DataInputStream(socket.getInputStream());
             DataOutputStream output = new DataOutputStream(socket.getOutputStream())) {
            String request = input.readUTF();
            if ("exit".equals(request)) {
                server.close();
                return;
            }
            handle(request, input, output);
        } catch (IOException e) {
            System.out.println("Failed to serve a client: " + e.getMessage());
        }
    }

    private static void handle(String request, DataInputStream input, DataOutputStream output) throws IOException {
        String[] parts = request.split(" ");
        switch (parts[0]) {
            case "PUT" -> putFile(parts.length > 1 ? parts[1] : null, readBytes(input), output);
            case "GET" -> getFile(lookup(parts), output);
            case "DELETE" -> deleteFile(lookup(parts), output);
            default -> output.writeUTF("400");
        }
    }

    /** Resolves "BY_ID 12" / "BY_NAME name" to a path in the data folder, or null if it can't exist. */
    private static Path lookup(String[] parts) {
        if (parts.length < 3) {
            return null;
        }
        String name = switch (parts[1]) {
            case "BY_NAME" -> parts[2];
            case "BY_ID" -> {
                try {
                    yield index.nameOf(Integer.parseInt(parts[2]));
                } catch (NumberFormatException e) {
                    yield null;
                }
            }
            default -> null;
        };
        return name == null ? null : resolve(name);
    }

    private static Path resolve(String fileName) {
        if (fileName.isBlank() || fileName.contains("/") || fileName.contains(File.separator)) {
            return null;
        }
        Path file = DATA_DIR.resolve(fileName).normalize();
        return DATA_DIR.equals(file.getParent()) ? file : null;
    }

    private static void getFile(Path file, DataOutputStream output) throws IOException {
        if (file == null || !Files.isRegularFile(file)) {
            output.writeUTF("404");
            return;
        }
        byte[] content = Files.readAllBytes(file);
        output.writeUTF("200");
        writeBytes(output, content);
    }

    private static void putFile(String fileName, byte[] content, DataOutputStream output) throws IOException {
        int id;
        Path file;
        if (fileName == null) {
            id = index.addGenerated();
            file = resolve(index.nameOf(id));
        } else {
            file = resolve(fileName);
            id = file == null ? -1 : index.add(fileName);
        }
        if (id < 0) {
            output.writeUTF("403");
            return;
        }
        try {
            assert file != null;
            Files.write(file, content);
        } catch (IOException e) {
            index.remove(file.getFileName().toString());
            output.writeUTF("403");
            return;
        }
        output.writeUTF("200 " + id);
    }

    private static void deleteFile(Path file, DataOutputStream output) throws IOException {
        if (file == null || !Files.deleteIfExists(file)) {
            output.writeUTF("404");
            return;
        }
        index.remove(file.getFileName().toString());
        output.writeUTF("200");
    }

    private static byte[] readBytes(DataInputStream input) throws IOException {
        byte[] bytes = new byte[input.readInt()];
        //blocks and loops internally until it has filled the entire array
        // (or throws EOFException if the stream ends first).
        input.readFully(bytes);
        return bytes;
    }

    private static void writeBytes(DataOutputStream output, byte[] bytes) throws IOException {
        output.writeInt(bytes.length);
        output.write(bytes);
    }
}
