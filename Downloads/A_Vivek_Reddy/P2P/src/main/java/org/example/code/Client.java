package org.example.code;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.code.model.FileInfo;
import org.example.code.model.FileToPeer;
import org.example.code.model.Request;

import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class Client {

    private static Map<String, Socket> socketMap = new ConcurrentHashMap<>();

    private static Integer CHUNK_SIZE = 256*1024;

    public static void main(String[] args) throws IOException {

        if(args.length<3){
            System.out.println("Usage: Java Client <server address> <server port> <client port>");
        }
        String serverAddress = args[0];
        int serverPort = Integer.parseInt(args[1]);
        int clientPort = Integer.parseInt(args[2]);

        try {
            Socket socket = new Socket(serverAddress, serverPort);
            System.out.println("Connected to the server");

            serverListener(clientPort);

            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
            OutputStream outputStream = socket.getOutputStream();
            InputStream inputStream = socket.getInputStream();
            ObjectOutputStream objOutputStream = new ObjectOutputStream(outputStream);
            ObjectInputStream in = new ObjectInputStream(inputStream);


            // Read input from the user and send it to the server
            String message;
            System.out.println("Allowed Commands:");
            System.out.println("1) fileRegister <srcDirectory>");
            System.out.println("2) List");
            System.out.println("3) FileLocReq <fileName>");
            System.out.println("4) download <filename from List cmd> <destinationPath>");

            while (true) {
                System.out.print("Enter the cmd: ");
                message = reader.readLine();
                String[] cmd = message.split(" ");

                // ##########################################################################
                //                         FILE REGISTER REQUEST
                //##############################################################################

                if (cmd[0].equalsIgnoreCase("fileRegister")) {
                    if (cmd.length < 2) {
                        System.out.println("Usage: fileRegister <srcDirectory>");
                        continue;
                    }
                    List<FileInfo> filesToReg = listFilesInDirectory(cmd[1]);
                    if (filesToReg.isEmpty()) continue;
                    Request request = new Request(cmd[0], filesToReg);
                    objOutputStream.writeObject(request);
                    objOutputStream.writeObject(clientPort);
                    objOutputStream.flush();
                    // Receive and display the server's response
                    byte[] buffer = new byte[1024];
                    int bytesRead = inputStream.read(buffer);
                    String response = new String(buffer, 0, bytesRead);
                    System.out.println("Server response: " + response);
                }

                // ##########################################################################
                //                        File List Request
                //##############################################################################

                if (cmd[0].equalsIgnoreCase("List")) {
                    Request request = new Request(cmd[0], null);
                    objOutputStream.writeObject(request);
                    objOutputStream.flush();

                    List<String> files = new ArrayList<>();
                    Map<String, List<FileToPeer>> fileToPeerMap = (Map<String, List<FileToPeer>>) in.readObject();
                    for (Map.Entry<String, List<FileToPeer>> entry : fileToPeerMap.entrySet()) {
                        if (!entry.getValue().isEmpty()) {
                            files.add(entry.getKey());
                        }
                    }
                    System.out.println(files);
                }

                // ##########################################################################
                //                         File Locations Request
                //##############################################################################

                if (cmd[0].equalsIgnoreCase("FileLocReq")) {
                    if (cmd.length < 2) {
                        System.out.println("Usage: FileLocReq <fileName>  Hint: Use List cmd to get List of files");
                        continue;
                    }
                    ObjectMapper objectMapper = new ObjectMapper();

                    List<FileToPeer> filesLocations = findFileLocation(cmd[1], objOutputStream, in);
                    if (filesLocations.isEmpty()) {
                        System.out.println("{}");
                    }
                    System.out.println(objectMapper.writeValueAsString(filesLocations));
                }


                // ##########################################################################
                //                         Download Request
                //##############################################################################

                if (cmd[0].equalsIgnoreCase("download")) {
                    // Usage: download <filename/scr> <destination>
                    if (cmd.length < 3) {
                        System.out.println("Usage: download <filename from List cmd> <destinationPath>");
                        continue;
                    }
                    String fileName = cmd[1];

                    List<FileToPeer> fileInfo = findFileLocation(fileName, objOutputStream, in);
                    Request request = new Request("countOfEachFileChunkAtPeers", null);
                    objOutputStream.writeObject(request);
                    objOutputStream.flush();
                    Map<String, Map<Integer, Integer>> countOfEachFileChunkAtPeers = (Map<String, Map<Integer, Integer>>) in.readObject();
                    Map<String, Map<Integer, List<FileToPeer>>> chunkToPeersMap = (Map<String, Map<Integer, List<FileToPeer>>>) in.readObject();
                    Map<Integer, Integer> countOfSpecificFileChunkAtPeers = countOfEachFileChunkAtPeers.get(cmd[1]);
                    List<Map.Entry<Integer, Integer>> entryList = new ArrayList<>(countOfSpecificFileChunkAtPeers.entrySet());
                    Collections.sort(entryList, Map.Entry.comparingByValue());

                    List<Integer> chunkIdList = new ArrayList<>(countOfSpecificFileChunkAtPeers.keySet());
                    Collections.sort(chunkIdList);
                    //send above data to server
                    // get the data check the hashcode and store into chunkfiles

                    Map<Integer, Integer> sortedMap = new LinkedHashMap<>();
                    for (Map.Entry<Integer, Integer> entry : entryList) {
                        sortedMap.put(entry.getKey(), entry.getValue());
                    }
                    Map<FileToPeer, List<Integer>> peerToChunkIdsMap = new HashMap<>();

                    for (Map.Entry<Integer, Integer> entry : sortedMap.entrySet()) {
                        FileToPeer peer = findFilePeerByChunkId(fileName, entry.getKey(), fileInfo, chunkToPeersMap);
                        peerToChunkIdsMap.computeIfAbsent(peer, k -> new ArrayList<>()).add(entry.getKey());
                    }

                    downloadFile(peerToChunkIdsMap, fileName, clientPort, cmd[2], objOutputStream, in, clientPort);

                }

                // ##########################################################################
                //                         Exit Request
                //##############################################################################
                if (cmd[0].equalsIgnoreCase("exit")) {
                    Request request = new Request(cmd[0], clientPort);
                    objOutputStream.writeObject(request);
                    objOutputStream.flush();
                    // Receive and display the server's response
                    byte[] buffer = new byte[1024];
                    int bytesRead = inputStream.read(buffer);
                    if (bytesRead > 0) {
                        String response = new String(buffer, 0, bytesRead);
                        System.out.println("Server response: " + response);
                    }
                    break;
                }
            }
            // Close the socket and ObjectOutputStream when done
            objOutputStream.close();
            socket.close();
            System.out.println("Connection closed.");
        } catch (IOException | RuntimeException | ClassNotFoundException | InterruptedException ex) {
            System.err.println(ex.getMessage());
        }
    }

    private static List<FileToPeer> findFileLocation(String fileName, ObjectOutputStream objOutputStream, ObjectInputStream in) throws IOException, ClassNotFoundException {
        Request request = new Request("FileLocReq", fileName);
        objOutputStream.writeObject(request);
        objOutputStream.flush();

        return (List<FileToPeer>) in.readObject();
    }

    private static void downloadFile(Map<FileToPeer, List<Integer>> peerToChunkIdsMap, String fileName, int clientPort, String destination, ObjectOutputStream centralServerObjOutputStream, ObjectInputStream centralServerObjInputStream, int port) throws IOException, InterruptedException {
        // get the peers and chunk list for that file.
        // order  chunks in rarest first order
        // get first rarest chunks for the peer that has those peers and randomly assign to peer to fetch
        // find hash for each chunk and send the hashcode along with data
        //
        String extension = getFileExtension(fileName);
//        Path filePath = Paths.get(fileName);
//        String relativeFileName = fileName;
        Path tempDir = Files.createTempDirectory(fileName+"_"+clientPort);
        Thread[] downloads = new Thread[peerToChunkIdsMap.size()];
        int i=0;
        for (Map.Entry<FileToPeer, List<Integer>> entry : peerToChunkIdsMap.entrySet()) {

            downloads[i] = new Thread(() -> downloadChunks(entry, fileName, tempDir, centralServerObjInputStream, centralServerObjOutputStream, extension, clientPort, destination));
            downloads[i++].start();
        }
        for(Thread thread : downloads){
            try {
                thread.join();
            } catch (InterruptedException e) {
                System.err.println("Thread interrupted: " + e.getMessage());
            }
        }

        combineChunkFilesToOneDestinationFile(tempDir, destination);
    }

    private static void combineChunkFilesToOneDestinationFile(Path tempDir, String destination){
        // combine the files and delete chunk files
        try {
            // Get a list of all chunk files in the temporary directory
            List<Path> chunkFiles = Files.list(tempDir)
                    .filter(p -> p.getFileName().toString().startsWith("chunk"))
                    .sorted()
                    .collect(Collectors.toList());

            chunkFiles.sort((p1, p2) -> {
                String name1 = p1.getFileName().toString();
                String name2 = p2.getFileName().toString();
                int num1 = Integer.parseInt(name1.substring(5, name1.lastIndexOf('.')));
                int num2 = Integer.parseInt(name2.substring(5, name2.lastIndexOf('.')));
                return Integer.compare(num1, num2);
            });

            // Open the combined file for writing
            Path path1 = Paths.get(destination);
            BufferedWriter fileWriter = Files.newBufferedWriter(path1);
            OutputStream combinedFileOutputStream = Files.newOutputStream(path1);

            for (Path file : chunkFiles) {
                byte[] fileData = Files.readAllBytes(file);
                combinedFileOutputStream.write(fileData);
            }
            combinedFileOutputStream.close();

            // Close the combined file
            fileWriter.close();

            // Delete the temporary directory and its contents
            Files.walk(tempDir)
                    .sorted((a, b) -> -a.compareTo(b))
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            System.err.println(e.getMessage());
                        }
                    });

            System.out.println("Downloaded Complete, check at" + destination);
        } catch (IOException e) {
            System.err.println(e.getMessage());
        }
    }

    private  static void downloadChunks(Map.Entry<FileToPeer, List<Integer>> entry, String fileName, Path tempDir, ObjectInputStream centralServerObjInputStream, ObjectOutputStream centralServerObjOutputStream, String extension, int clientPort, String destination){
            try {
                Socket socket1 = new Socket(entry.getKey().getIpAddress(), entry.getKey().getPort());
                OutputStream outputStream = socket1.getOutputStream();
                InputStream inputStream = socket1.getInputStream();
                ObjectOutputStream objectOutputStream1 = new ObjectOutputStream(outputStream);
                ObjectInputStream in1 = new ObjectInputStream(inputStream);
                List<Integer> chunkIndices = entry.getValue();

                for (int chunkId : chunkIndices) {
                    List<Object> fileNameChunkIdMap = new ArrayList<>();
                    fileNameChunkIdMap.add(entry.getKey().getRealPath());
                    fileNameChunkIdMap.add(chunkId);
                    Request request = new Request("download", fileNameChunkIdMap);
                    objectOutputStream1.writeObject(request);
                    objectOutputStream1.flush();

                    byte[] chunkData = (byte[]) in1.readObject();
                    String receivedHashCode = (String) in1.readObject();
                    String recalculatedHashCode = hashChunkData(chunkData);
                    if (recalculatedHashCode.equals(receivedHashCode)) {
                        if(writeToFile(chunkData, chunkId, tempDir, extension)==0){
                            System.out.println("Chunk"+chunkId +" - file : " + fileName + " from "+"Peer: "+entry.getKey().getIpAddress()+":" +entry.getKey().getPort());
                        };
                        if(registerChunk(fileName, chunkId, centralServerObjOutputStream, centralServerObjInputStream, clientPort,destination)==1){
                            System.out.println("Chunk"+chunkId +" - file : " + fileName + " registered to server");
                        };
                    }
                }

                Request request = new Request("exit", null);
                objectOutputStream1.writeObject(request);
                objectOutputStream1.flush();
                outputStream.flush();

            } catch (IOException | ClassNotFoundException e) {
                System.err.println(e.getMessage());
            }
    }

    private static int writeToFile(byte[] chunkData, int chunkId, Path tempDir, String extension) throws IOException {

        Path outputPath = Files.createFile(tempDir.resolve("chunk" + chunkId + "." + extension));

        // Create and write the chunk to a new file
        Files.write(outputPath, chunkData);
        return 0;
    }

    private static String getFileExtension(String filePath){
        Path path = Paths.get(filePath);
        String extension = "";

        // Get the file extension using Path
        String fileName = path.getFileName().toString();
        int dotIndex = fileName.lastIndexOf('.');

        if (dotIndex > 0 && dotIndex < fileName.length() - 1) {
            extension = fileName.substring(dotIndex + 1);
        }
        return extension;
    }

    private static byte[] readChunkData(String fileName, int chunkId){
        File sourceFile = new File(fileName.replace("##SPACE##"," "));
        byte[] buffer = new byte[CHUNK_SIZE];
        try {
            FileInputStream fileInputStream = new FileInputStream(sourceFile);

            long offset = fileInputStream.skip((long) chunkId * CHUNK_SIZE);
            if (offset == (long) chunkId * CHUNK_SIZE) {
                int bytesRead = fileInputStream.read(buffer, 0, CHUNK_SIZE);
                if (bytesRead != -1) {
                    fileInputStream.close();
                }
            }
        } catch (IOException e) {
            System.err.println(e.getMessage());
        }
        return buffer;
    }

    private static String getFilesMapAsJsonString(Map<String, List<FileToPeer>> fileToPeerMap) throws JsonProcessingException {
        Map<String, Long> files = new HashMap<>();
        for (Map.Entry<String, List<FileToPeer>> entry : fileToPeerMap.entrySet()) {
            String fileName = entry.getKey();
            List<FileToPeer> fileToPeerList = entry.getValue();
            files.put(fileName,fileToPeerList.get(0).getFileSize());
        }
        ObjectMapper objectMapper = new ObjectMapper();
        // Serialize the POJO to JSON
        return objectMapper.writeValueAsString(files);
    }


    private static List<FileInfo> listFilesInDirectory(String directoryPath) {
        List<FileInfo> filesToRegister = new ArrayList<>();
        File directory = new File(directoryPath);

        // Check if the directory exists
        if (directory.exists() && directory.isDirectory()) {
            // Get a list of files in the directory
            File[] files = directory.listFiles();

            // Iterate through the files and add them to the list
            assert files != null;
            for (File file : files) {
                if (file.isFile()) {
                    String fileName = file.getAbsolutePath().replace(" ","##SPACE##");
                    long fileLength = file.length();
                    FileInfo fileToRegister = new FileInfo(fileName, fileLength);
                    filesToRegister.add(fileToRegister);
                }
            }
        } else {
            System.out.println("The specified directory does not exist.");
        }
        return filesToRegister;
    }

    static int registerChunk(String fileName, int index, ObjectOutputStream objectOutputStream, ObjectInputStream objectInputStream, int peerPort, String realPath) {
        try {

            List<Object> args = new ArrayList<>();
            args.add(fileName);
            args.add(index);
            args.add(peerPort);
            args.add(realPath);
            Request request = new Request("chunkReg", args);

            // Send request to the server
            objectOutputStream.writeObject(request);
            objectOutputStream.flush();

        } catch (IOException e) {
            System.err.println(e.getMessage());
        }
        return 1;
    }

    public static FileToPeer findFilePeerByChunkId(String filename, int chunkId, List<FileToPeer> fileInfo, Map<String, Map<Integer, List<FileToPeer>>> chunkToPeersMap) {
        // Retrieve the map for the given filename
        Map<Integer, List<FileToPeer>> chunkMap = chunkToPeersMap.get(filename);

        if (chunkMap != null) {
            // Retrieve the list of peers for the given chunkId
            List<FileToPeer> peersWithChunk = chunkMap.get(chunkId);

            if (peersWithChunk != null && !peersWithChunk.isEmpty()) {
                // Choose a random peer from the list
                int randomIndex = new Random().nextInt(peersWithChunk.size());
                FileToPeer randomPeer = peersWithChunk.get(randomIndex);

                // Check if the random peer is in the fileInfo list (based on ipAddress and port)
                for (FileToPeer peerInfo : fileInfo) {
                    if (peerInfo.getIpAddress().equals(randomPeer.getIpAddress()) &&
                            peerInfo.getPort() == randomPeer.getPort()) {
                        return peerInfo; // Found a matching peer in fileInfo
                    }
                }
            }
        }
        return null; // No matching peer found
    }

    private static String hashChunkData(byte[] chunkData){
        String hashValue = "";
        try {
            // Create a MessageDigest instance with a specific hashing algorithm, such as SHA-256
            MessageDigest md = MessageDigest.getInstance("SHA-256");

            // Update the MessageDigest with the input data
            md.update(chunkData);

            // Calculate the hash value
            byte[] hashBytes = md.digest();

            // Convert the hash bytes to a hexadecimal string
            StringBuilder hashString = new StringBuilder();
            for (byte b : hashBytes) {
                hashString.append(String.format("%02x", b));
            }

            // Print and store the hash value
            hashValue = hashString.toString();

        } catch (NoSuchAlgorithmException e) {
            System.err.println("Hashing algorithm not found.");
        }
        return hashValue;
    }

    private static void serverListener(int clientPort) {
        Thread serverListenerThread = new Thread(() -> {
            try {
                ServerSocket serverSocket1 = new ServerSocket(clientPort);
                System.out.println("Server listener started on port " + serverSocket1.getLocalPort());

                while (true) {
                    Socket clientSocket1 = serverSocket1.accept();
                    new PeerHandler(clientSocket1).run();
                }
            } catch (IOException e) {
                System.err.println(e.getMessage());;
            }
        });
        serverListenerThread.start();
    }

    private static class PeerHandler implements Runnable{
        private final Socket peerSocket;
        private final ObjectOutputStream objectOutputStream;
        private final ObjectInputStream objectInputStream;

        public PeerHandler(Socket peerSocket) throws IOException {
            this.peerSocket = peerSocket;
            // Get input and output streams for the Peer
            InputStream inputStream = peerSocket.getInputStream();
            OutputStream outputStream = peerSocket.getOutputStream();
            objectInputStream = new ObjectInputStream(inputStream);
            objectOutputStream = new ObjectOutputStream(outputStream);
        }

        @Override
        public void run() {
            try {
                System.out.println("Accepted connection from " + peerSocket.getInetAddress().getHostAddress()+":"+peerSocket.getLocalPort());

                while (true) {
                    Request peerRequest = (Request) objectInputStream.readObject();

                    if (peerRequest.getCommand().equalsIgnoreCase("download")) {
                        List<Object> list = (List<Object>) peerRequest.getValues();
                        byte[] chunkData;
                        chunkData = readChunkData((String) list.get(0), (Integer) list.get(1));
                        String hashCode = hashChunkData(chunkData);
                        objectOutputStream.writeObject(chunkData);
                        objectOutputStream.writeObject(hashCode);
                        objectOutputStream.flush();
                    }
                    if (peerRequest.getCommand().equalsIgnoreCase("exit")) {
                        break;
                    }

                }
                peerSocket.close();
            } catch (IOException | ClassNotFoundException e) {
                System.err.println(e.getMessage());
            }
        }
    }
}
