package org.example.code;

import org.example.code.model.FileInfo;
import org.example.code.model.FileToPeer;
import org.example.code.model.Request;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Server {
    private static final Map<String, List<FileToPeer>> fileToPeerMap = new ConcurrentHashMap<>();
    private static final Map<String, Map<Integer, Integer>> countOfEachFileChunkAtPeers = new ConcurrentHashMap<>();
    private static final Map<String, Map<Integer, List<FileToPeer>>> chunkToPeersMap = new ConcurrentHashMap<>();
    private static final Map<String, Socket> socketMap = new ConcurrentHashMap<>();

    private static final Integer CHUNK_SIZE = 256*1024;

    public static void main(String[] args) {
        if(args.length<2){
            System.out.println("Usage: Java Server <server address> <server port>");
        }
        String serverAddress = args[0];
        int port = Integer.parseInt(args[1]);
        startServer(port);
    }

    public static void startServer(int port){
        try {
            ServerSocket serverSocket = new ServerSocket(port);
            System.out.println("Server is listening on port " + port);

            while (true) {
                // Accept client connection
                Socket clientSocket = serverSocket.accept();
                String clientIdentifier = getClientIdentifier(clientSocket);
                socketMap.put(clientIdentifier, clientSocket);

                // Create a new thread to handle the client
                Thread clientThread = new Thread(new ClientHandler(clientSocket));
                clientThread.start();
            }
        } catch (IOException e) {
            System.err.println(e.getMessage());
        }
    }

    private static String getClientIdentifier(Socket socket) {
        // Create a unique identifier for the client based on its IP and port
        return socket.getInetAddress().getHostAddress() + ":" + socket.getPort();
    }

    private static class ClientHandler implements Runnable {
        private final Socket clientSocket;
        private final OutputStream outputStream;
        private final ObjectOutputStream objectOutputStream;
        private final ObjectInputStream objectInputStream;

        public ClientHandler(Socket clientSocket) throws IOException {

            this.clientSocket = clientSocket;
            InputStream inputStream = clientSocket.getInputStream();
            outputStream = clientSocket.getOutputStream();
            objectInputStream = new ObjectInputStream(inputStream);
            objectOutputStream = new ObjectOutputStream(outputStream);

        }

        @Override
        public void run() {

                System.out.println("Accepted connection from " + clientSocket.getInetAddress());
                // Get input and output streams for the client
                String clientAddress = clientSocket.getInetAddress().getHostAddress();
            try {
                while (true) {
                    Request peerRequest = (Request) objectInputStream.readObject();


                    if (peerRequest.getCommand().equalsIgnoreCase("fileRegister")) {
                        List<FileInfo> filesToReg = (List<FileInfo>) peerRequest.getValues();
                        List<String> filesToAdd = new ArrayList<>();
                        int peerPort = (int) objectInputStream.readObject();
                        String response;
                        for (FileInfo fileInfo : filesToReg) {
                            String filename = getFileNameAndDirectory(fileInfo.getFileName());
                            String realfilename = fileInfo.getFileName();
                            long fileSize = fileInfo.getSize();
                            Set<Integer> chunkIds = new HashSet<>();
                            long chunkSize = getNumberOfChunks(fileSize);

                            Map<Integer, Integer> chunkCountMap = countOfEachFileChunkAtPeers.computeIfAbsent(filename, k -> new HashMap<>());
                            Map<Integer, List<FileToPeer>> chunkPeerInfo = chunkToPeersMap.getOrDefault(filename, new HashMap<>());

                            for (int j = 0; j < chunkSize; j++) {
                                chunkIds.add(j);
                                chunkCountMap.put(j, chunkCountMap.getOrDefault(j, 0) + 1);
                                List<FileToPeer> peers = chunkPeerInfo.getOrDefault(j, new ArrayList<>());
                                if(!peers.stream().anyMatch(peer -> peer.getPort() == peerPort && peer.getIpAddress().equals(clientAddress))) {
                                    peers.add(new FileToPeer(clientAddress, peerPort, fileSize, null, null));
                                    chunkPeerInfo.put(j, peers);
                                }
                            }
                            chunkToPeersMap.put(filename, chunkPeerInfo);

                            List<FileToPeer> peerInfo = fileToPeerMap.getOrDefault(filename, new ArrayList<>());
                            if(!peerInfo.stream().anyMatch(peer -> peer.getPort() == peerPort && peer.getIpAddress().equals(clientAddress))) {
                                peerInfo.add(new FileToPeer(clientAddress, peerPort, fileSize, chunkIds, realfilename));
                                fileToPeerMap.put(filename, peerInfo);
                                filesToAdd.add(filename);
                            }
                        }

                        response = "Registered, files: " + filesToAdd;
                        // Send the response back to the client
                        byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
                        outputStream.write(responseBytes);
                        outputStream.flush();
                    }

                    if (peerRequest.getCommand().equalsIgnoreCase("List")) {
                        objectOutputStream.writeObject(fileToPeerMap);
                        objectOutputStream.flush();
                    }

                    if (peerRequest.getCommand().equalsIgnoreCase("FileLocReq")) {
                        objectOutputStream.writeObject(fileToPeerMap.get(peerRequest.getValues().toString()));
                        objectOutputStream.flush();
                    }

                    if(peerRequest.getCommand().equals("chunkReg")){
                        List<Object> fileChunk = (List<Object>) peerRequest.getValues();
                        String fileName = (String) fileChunk.get(0);
                        int index = (int) fileChunk.get(1);
                        int peerPort = (int) fileChunk.get(2);
                        String realPath = (String) fileChunk.get(3);
                        List<FileToPeer> listOfPeersForSpecificFile = fileToPeerMap.get(fileName);
                        boolean found = false;

                        for (FileToPeer peer : listOfPeersForSpecificFile) {
                            if (peer.getPort() == peerPort && peer.getIpAddress().equals(clientAddress)) {
                                found = true;
                                break;
                            }
                        }

                        if(!found){
                            FileToPeer newPeer = new FileToPeer(clientAddress, peerPort, 0, new HashSet<>(), realPath);
                            newPeer.getChunkIds().add(index);
                            listOfPeersForSpecificFile.add(newPeer);
                        }
                        Map<Integer, Integer> chunkCountMap = countOfEachFileChunkAtPeers.computeIfAbsent(fileName, k -> new HashMap<>());
                        chunkCountMap.put(index, chunkCountMap.getOrDefault(index, 0) + 1);

                        chunkToPeersMap.computeIfAbsent(fileName, k -> new HashMap<>())
                                .computeIfAbsent(index, k -> new ArrayList<>())
                                .add(new FileToPeer(clientAddress, peerPort, 0, null, realPath));
                    }

                    if(peerRequest.getCommand().equals("countOfEachFileChunkAtPeers")){
                        objectOutputStream.writeObject(countOfEachFileChunkAtPeers);
                        objectOutputStream.writeObject(chunkToPeersMap);
                        objectOutputStream.flush();
                    }

                    if (peerRequest.getCommand().equalsIgnoreCase("exit")) {
                        int peerPort = (int) peerRequest.getValues();

                        Set<Integer> chunkIdsToRemove = new HashSet<>();
                        fileToPeerMap.values().forEach(peerList -> {
                            // Collect the chunkIds associated with the exited peer
                            peerList.stream()
                                    .filter(peer -> peer.getIpAddress().equals(clientAddress) && peer.getPort() == peerPort)
                                    .forEach(peer -> chunkIdsToRemove.addAll(peer.getChunkIds()));
                            // remove the peer
                            peerList.removeIf(peer -> peer.getIpAddress().equals(clientAddress) && peer.getPort() == peerPort);
                        });

                        List<String> keysToRemove = new ArrayList<>();
                        for (Map.Entry<String, List<FileToPeer>> entry : fileToPeerMap.entrySet()) {
                            if (entry.getValue().isEmpty()) {
                                keysToRemove.add(entry.getKey());
                            }
                        }
                        // Remove the identified keys
                        for (String key : keysToRemove) {
                            fileToPeerMap.remove(key);
                        }

                        // Iterate through the countOfEachFileChunkAtPeers and decrement the count for the chunkIds
                        countOfEachFileChunkAtPeers.forEach((filename, chunkCountMap) ->
                                chunkIdsToRemove.forEach(chunkId ->
                                        chunkCountMap.compute(chunkId, (key, count) -> count == null ? 0 : count - 1)
                                )
                        );

                        chunkToPeersMap.values().forEach(chunkMap ->
                                chunkMap.values().forEach(peerList -> peerList.removeIf(peer -> peer.getIpAddress().equals(clientAddress) && peer.getPort() == peerPort))
                        );

                        String response = "Connection closed for " + clientAddress +":"+ peerPort;
                        byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
                        outputStream.write(responseBytes);
                        outputStream.flush();

                        clientSocket.close();
                        // Send the response back to the client
                        System.out.println("Connection closed for " + clientAddress);
                        break;
                    }

                }
            } catch (IOException e) {
                System.err.println(e.getMessage());
            } catch (ClassNotFoundException e) {
                System.err.println(e.getMessage());
            }
        }

        private String getFileNameAndDirectory(String filePath){

            // Parse the absolute path
            Path path = Paths.get(filePath);

            // Extract the filename
            String filename = path.getFileName().toString();

            // Extract the remaining path (excluding the filename)
            Path parentPath = path.getParent();
            String remainingPath = parentPath != null ? parentPath.toString() : "";

            List<String> fileNameAndDirectory = new ArrayList<>();
            fileNameAndDirectory.add(filename);
            fileNameAndDirectory.add(remainingPath);

            return filename;
        }

        private String getAbsolutePath(String directoryPath, String filename){
            Path absolutePath = Paths.get(directoryPath, filename);
            // Convert the Path to a String
            return absolutePath.toString();
        }

        private long getNumberOfChunks(long fileSize) {
            long chunkNumber = fileSize / CHUNK_SIZE;
            if ((chunkNumber * CHUNK_SIZE) < fileSize) {
                chunkNumber += 1;
            }
            return chunkNumber;
        }
    }
}

