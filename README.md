# File Server

A client–server file storage app in Java, built as the final stage of the Hyperskill **File Server** project.
The client uploads any file (text, images, anything) to the server, and can download or delete it later
by its numeric **ID** or its **name**.

## Features

- **Binary-safe transfer**: files are sent as a length prefix followed by the raw bytes, so any file type works.
- **Multithreaded server**: the main thread only accepts connections, and each request runs in a thread pool.
- **Persistent IDs**: a thread-safe map of IDs to file names is saved to disk after every change.
  After a restart, stored files can still be found by ID, and new IDs continue where they left off.
- **Access by ID or name**: `GET` and `DELETE` work with `BY_ID` or `BY_NAME`.
- **Generated names**: if you don't name an upload, the server names it `file_<n>`.

## Project layout

```
File Server/task/
├── src/
│   ├── client/
│   │   ├── Main.java        # interactive client
│   │   └── data/            # files to upload / downloaded files
│   └── server/
│       ├── Main.java        # socket server + thread pool
│       ├── FileIndex.java   # persistent, synchronized id <-> name map
│       ├── data/            # files stored on the server
│       └── index.txt        # saved index (created at runtime)
└── test/                    # Hyperskill stage tests
```

## Running

Both programs look up their `data/` folders relative to the working directory,
so run them from `File Server/task`:

```bash
cd "File Server/task"
javac -d out src/server/*.java src/client/*.java

# terminal 1
java -cp out server.Main

# terminal 2 (one request per run)
java -cp out client.Main
```

The server listens on `127.0.0.1:23456`. Put the files you want to upload in `src/client/data/`.

To run the stage tests from the repository root:

```bash
./gradlew :File_Server-task:test
```

## Example session

```
Enter action (1 - get a file, 2 - save a file, 3 - delete a file): > 2
Enter name of the file: > my_cat.jpg
Enter name of the file to be saved on server: >
The request was sent.
Response says that file is saved! ID = 23

Enter action (1 - get a file, 2 - save a file, 3 - delete a file): > 1
Do you want to get the file by name or by id (1 - name, 2 - id): > 2
Enter id: > 23
The request was sent.
The file was downloaded! Specify a name for it: > cat.jpg
File saved on the hard drive!

Enter action (1 - get a file, 2 - save a file, 3 - delete a file): > 3
Do you want to delete the file by name or by id (1 - name, 2 - id): > 2
Enter id: > 23
The request was sent.
The response says that this file was deleted successfully!
```

Entering `exit` as the action shuts the server down. This exists for the automated tests.

## Protocol

Every request begins with a header string sent with `writeUTF`.
File contents are sent as `writeInt(length)` followed by the raw bytes.

| Request                                    | Body        | Success response        | Failure |
|--------------------------------------------|-------------|-------------------------|---------|
| `PUT` or `PUT <name>`                      | file bytes  | `200 <id>`              | `403`   |
| `GET BY_ID <id>` / `GET BY_NAME <name>`    | —           | `200`, then file bytes  | `404`   |
| `DELETE BY_ID <id>` / `DELETE BY_NAME <name>` | —        | `200`                   | `404`   |
| `exit`                                     | —           | server stops            | —       |
