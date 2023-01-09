package com.fileportal.portal;

public class FileDownloadException extends Exception{
    private final String message;

    public FileDownloadException(String message) {
        this.message = message;
    }

    @Override
    public String getMessage() {
        return this.message;
    }
}
