package org.example.code.model;

import java.io.Serializable;
import java.util.List;

public class FileInfo implements Serializable {
    private static final long serialVersionUID = 1L; // Add a serialVersionUID

    public String fileName;
    public long size;
    public List <FileToPeer> peers;

    public FileInfo(String fileName, long size){
        this.fileName = fileName;
        this.size = size;
    }

    public String getFileName() {
        return this.fileName;
    }

    public long getSize() {
        return this.size;
    }
}
