package client;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Scanner;

public class Main {

    private static final String ADDRESS = "127.0.0.1";
    private static final int PORT = 23456;
    private static final int CONNECT_ATTEMPTS = 50;
    private static final Path DATA_DIR = Paths.get(System.getProperty("user.dir"), "src", "client", "data");

    public static void main(String[] args) throws Exception {
        Files.createDirectories(DATA_DIR);
        Scanner scanner = new Scanner(System.in);
        System.out.print("Enter action (1 - get a file, 2 - save a file, 3 - delete a file): ");
        String action = scanner.nextLine().trim();

        // for PUT the file content has to be read before connecting, so a missing file never reaches the server
        byte[] content = null;
        String request;
        switch (action) {
            case "1":
                request = "GET " + askFileOption("get", scanner);
                break;
            case "2":
                System.out.print("Enter name of the file: ");
                Path local = DATA_DIR.resolve(scanner.nextLine().trim());
                if (!Files.isRegularFile(local)) {
                    System.out.println("The file " + local.getFileName() + " doesn't exist in " + DATA_DIR);
                    return;
                }
                content = Files.readAllBytes(local);
                System.out.print("Enter name of the file to be saved on server: ");
                String serverName = scanner.hasNextLine() ? scanner.nextLine().trim() : "";
                request = serverName.isEmpty() ? "PUT" : "PUT " + serverName;
                break;
            case "3":
                request = "DELETE " + askFileOption("delete", scanner);
                break;
            case "exit":
                // you shouldn't allow this behavior in a normal situation when no testing needs to be done.
                request = "exit";
                break;
            default:
                System.out.println("Unknown action: " + action);
                return;
        }

        try (Socket socket = connect();
             DataInputStream input = new DataInputStream(socket.getInputStream());
             DataOutputStream output = new DataOutputStream(socket.getOutputStream())) {
            output.writeUTF(request);
            if (content != null) {
                output.writeInt(content.length);
                output.write(content);
            }
            output.flush();
            System.out.println("The request was sent.");

            String[] response = request.equals("exit") ? null : input.readUTF().split(" ", 2);
            switch (action) {
                case "1" -> {
                    assert response != null;
                    receiveFile(response, input, scanner);
                }
                case "2" -> System.out.println("200".equals(response[0])
                        ? "Response says that file is saved! ID = " + response[1]
                        : "The response says that creating the file was forbidden!");
                case "3" -> System.out.println("200".equals(response[0])
                        ? "The response says that this file was deleted successfully!"
                        : "The response says that this file is not found!");
            }
        }
    }

    /** Retries for a few seconds, since the server may still be starting up. */
    private static Socket connect() throws IOException {
        for (int attempt = 1; ; attempt++) {
            try {
                return new Socket(InetAddress.getByName(ADDRESS), PORT);
            } catch (ConnectException e) {
                if (attempt == CONNECT_ATTEMPTS) {
                    throw e;
                }
                //Thread.sleep(100);
            }
        }
    }

    private static void receiveFile(String[] response, DataInputStream input, Scanner scanner) throws IOException {
        if (!"200".equals(response[0])) {
            System.out.println("The response says that this file is not found!");
            return;
        }
        byte[] content = new byte[input.readInt()];
        input.readFully(content);

        System.out.print("The file was downloaded! Specify a name for it: ");
        Files.write(DATA_DIR.resolve(scanner.nextLine().trim()), content);
        System.out.println("File saved on the hard drive!");
    }

    /** Asks whether to address the file by name or id and returns the "BY_NAME x" / "BY_ID n" part. */
    private static String askFileOption(String verb, Scanner scanner) {
        System.out.print("Do you want to " + verb + " the file by name or by id (1 - name, 2 - id): ");
        String option = scanner.nextLine().trim();
        return switch (option) {
            case "1" -> {
                System.out.print("Enter name of the file: ");
                yield "BY_NAME " + scanner.nextLine().trim();
            }
            case "2" -> {
                System.out.print("Enter id: ");
                yield "BY_ID " + scanner.nextLine().trim();
            }
            default -> throw new IllegalArgumentException("Unexpected option: " + option);
        };
    }
}
