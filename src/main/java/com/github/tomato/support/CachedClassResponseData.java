package com.github.tomato.support;

public class CachedClassResponseData {
    private String className;
    private String response;

    public CachedClassResponseData(String className, String response) {
        this.className = className;
        this.response = response;
    }

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public String getResponse() {
        return response;
    }

    public void setResponse(String response) {
        this.response = response;
    }
}
