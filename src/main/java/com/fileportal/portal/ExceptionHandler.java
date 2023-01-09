package com.fileportal.portal;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;

import java.io.FileNotFoundException;
import java.io.IOException;

@ControllerAdvice
public class ExceptionHandler {

    @org.springframework.web.bind.annotation.ExceptionHandler({FileNotFoundException.class, IOException.class})
    public void handleFileNotFound(HttpServletResponse response, FileNotFoundException ex) throws IOException {
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        response.getWriter().write(ex.getMessage());
    }

    @org.springframework.web.bind.annotation.ExceptionHandler(FileDownloadException.class)
    public void handeFileDownloadFailure(HttpServletResponse response, FileNotFoundException ex) throws IOException {
        response.setStatus(HttpServletResponse.SC_EXPECTATION_FAILED);
        response.getWriter().write(ex.getMessage());
    }
}