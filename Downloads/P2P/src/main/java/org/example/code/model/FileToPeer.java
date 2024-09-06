package org.example.code.model;

import java.io.Serializable;
import java.util.Set;

public class FileToPeer implements Serializable {
    private static final long serialVersionUID = 1L; // Add a serialVersionUID

    private String ipAddress;
    private int port;
    private long fileSize;
    private Set<Integer> chunkIds;
    private String realPath;



    public FileToPeer(String ipAddress, int port, long fileSize, Set<Integer> chunkIds, String realPath){
        this.ipAddress = ipAddress;
        this.port = port;
        this.fileSize = fileSize;
        this.chunkIds = chunkIds;
        this.realPath = realPath;
    }

    public String getIpAddress(){
        return this.ipAddress;
    }

    public int getPort() {
        return this.port;
    }

    public long getFileSize() {
        return this.fileSize;
    }

    public Set<Integer> getChunkIds(){ return this.chunkIds; }

    public String getRealPath(){
        return this.realPath;
    }

    public void setRealPath(String realPath) { this.realPath = realPath;}
}
