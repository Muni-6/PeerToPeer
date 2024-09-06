package org.example.code.model;

import java.io.Serializable;

public class Request implements Serializable {
    private static final long serialVersionUID = 1L; // Add a serialVersionUID

    private String Command;

    private Object values;

    public Request(String command, Object values){
        this.Command = command;
        this.values = values;
    }

    public String getCommand(){
        return this.Command;
    }

    public  Object getValues(){
        return this.values;
    }

}
