# Post-Quantum Cryptography (PQC) FTP Project

## Description
This FTP project includes both a post-quantum cryptography (PQC) implementation and a plaintext implementation. Both programs were developed for experimental purposes and are **not recommended for use in production environments**.
Both the client and server use `./files` as the default file directory. Put your own files in that directory before running. Both implementations were originally tested with file sizes of 1 MB, 10 MB, 50 MB, and 100 MB.

## Third-Party Dependencies
This application requires liboqs and liboqs-java. Users must install liboqs and compile liboqs-java before running the PQC FTP client or server. Refer to the official repositories for installation instructions.

## Running PQC-FTP

### Prerequisites
To run the PQC implementation, you must install `liboqs` on both the client and server machines. You also need a compiled `liboqs-java.jar` on both machines.
The commands below use a Unix-style classpath separator (`:`). Replace `$HOME/path/to/jar/file/liboqs-java.jar` with the actual path to your JAR file.

### 1. Generate the server identity (`GenerateServerIdentity.java`)
This class generates the server's public and private keys for ML-DSA authentication.
After running the following commands, copy `server_mldsa_public.key` to the client machine. Keep `server_mldsa_public.key` and `server_mldsa_secret.key` on the server. The client uses the trusted public key to authenticate the server.
For security testing, you can replace the trusted public key on the client with an untrusted test key by renaming the untrusted key file to the expected trusted-key filename. Authentication should then fail.
**Security note:** Generate your own keys locally. Do not commit real private keys, including `server_mldsa_secret.key`, to a public GitHub repository.

```bash
javac -cp ".:$HOME/path/to/jar/file/liboqs-java.jar" GenerateServerIdentity.java
java -cp ".:$HOME/path/to/jar/file/liboqs-java.jar" GenerateServerIdentity
```

### 2. Start the server (`Server.java`)
Compile and run the server using the commands below. You will be prompted to enter two port numbers: one for client requests and another for file transfers. Start the server before running the client so that the client has a server to connect to.

```bash
javac -cp ".:$HOME/path/to/jar/file/liboqs-java.jar" Server.java
java -cp ".:$HOME/path/to/jar/file/liboqs-java.jar" Server
```

### 3. Start the client (`Client.java`)
Compile and run the client using the commands below. You will be prompted for the server's IP address and the two port numbers configured on the server.

```bash
javac -cp ".:$HOME/path/to/jar/file/liboqs-java.jar" Client.java
java -cp ".:$HOME/path/to/jar/file/liboqs-java.jar" Client
```

### Example output
If the program runs successfully, your output should resemble the following examples. The IP address and port numbers shown are examples; use the values appropriate for your network.

#### Client side

```text
[Client] What is the IP Address of the Server
>192.168.0.127
[Client] Please enter port number Client Requests
>1111
[Client] Please enter port number File Transfers
>1112
[PQC] Starting authenticated ML-KEM-768 handshake...
[PQC] ML-DSA-65 signature verified.
[PQC] Server authenticated successfully.
[PQC] ML-KEM ciphertext sent.
[PQC] Handshake transcript hashed.
[PQC] Authenticated handshake complete.
[Client] ML-KEM shared secret established.
[Client] HKDF session keys derived.
[Client] Authenticated PQC connection established.
[Client] Type in command followed by parameters
GET [Server Path] [Client Path]
PUT [Client Path] [Server Path]
CD [Server Path]
LS
PWD
QUIT
>
```

#### Server side

```text
[Server] Please enter port number Client Requests
>1111
[Server] Please enter port number File Transfers
>1112
[Server] Client Connected
[Server] Starting PQC handshake...
[PQC] Starting authenticated server handshake...
[PQC] Handshake signed with ML-DSA-65.
[PQC] Handshake transcript hashed.
[PQC] Authenticated ML-KEM handshake complete.
[Server] ML-KEM shared secret established.
[Server] HKDF session keys derived.
[Server] Secure PQC connection established.
```

## Running Plaintext FTP
The plaintext implementation is simpler to run because it does not require the PQC library.

### 1. Start the server (`Server.java`)
Compile and run the server using the commands below. You will be prompted for two port numbers: one for client requests and another for file transfers. Start the server before running the client.

```bash
javac Server.java
java Server
```

### 2. Start the client (`Client.java`)
Compile and run the client using the commands below. You will be prompted for the server's IP address and the two port numbers configured on the server.

```bash
javac Client.java
java Client
```

### Example output
If the program runs successfully, your output should resemble the following examples.

#### Client side

```text
[Client] What is the IP Address of the Server
>192.168.0.127
[Client] Please enter port number Client Requests
>1111
[Client] Please enter port number File Transfers
>1112
[Client] Type in command followed by parameters
GET [Server Path] [Client Path]
PUT [Client Path] [Server Path]
CD [Server Path]
LS
PWD
QUIT
>
```

#### Server side

```text
[Server] Please enter port number Client Requests
>1111
[Server] Please enter port number File Transfers
>1112
[Server] Client Connected
```

## Command Reference
Both implementations support the following commands.

### GET — Download a file
`GET [Server Path] [Client Path]` retrieves a file from the server and saves it at the specified client path.

```text
GET testS.pdf testS.pdf
GET /path/to/file/testS.pdf /path/to/file/testS.pdf
GET "/path/to/file/testS.pdf" "/path/to/file/testS.pdf"
```

### PUT — Upload a file
`PUT [Client Path] [Server Path]` sends a file from the client to the server.

```text
PUT testC.pdf testC.pdf
PUT /path/to/file/testC.pdf /path/to/file/testC.pdf
PUT "/path/to/file/testC.pdf" "/path/to/file/testC.pdf"
```

### CD — Change the server directory
`CD [Server Path]` changes the server directory used for file operations. The default directory is `./files`.

```text
CD /full/path/to/new/directory
CD path/to/nearby/directory
CD ..
```

Use `CD ..` to move up one directory.

### LS — List files
`LS` lists files in the current server directory. The default directory is `./files`.

```text
LS
```

### PWD — Print the current directory
`PWD` displays the current server directory. The default directory is `./files`.

```text
PWD
```

### QUIT — End the client session
`QUIT` exits the client session.

```text
QUIT
```
