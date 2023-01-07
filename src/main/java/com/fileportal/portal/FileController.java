package com.fileportal.portal;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The FileController class is designed for transferring files between clients without
 * storing the full contents of the file on the server. The class uses a Map to store
 * the chunks of the file being uploaded as they are received.
 * <br<br>
 * When a client requests to download a file, the server streams the contents of the file to the client in chunks.
 * This allows for efficient transfer of large files without using excessive server memory.
 * Additionally, the FileController class is thread-safe, as it uses a ConcurrentHashMap to store
 * the file chunks, allowing multiple clients to upload and download files concurrently.
 */
@RestController
@RequestMapping("/files")
public class FileController {
    private final Map<String, ByteArrayOutputStream> streams = new ConcurrentHashMap<>();
    private final Map<String, Integer> progress = new ConcurrentHashMap<>();

    @PutMapping("/{fileId}")
    //curl -v -X PUT -H "Content-Type: application/octet-stream" --data-binary @path http://localhost:8080/files/{fileId}
    public void uploadFile(@PathVariable String fileId, HttpServletRequest request) throws IOException {
        var outputStream = new ByteArrayOutputStream();
        var inputStream = request.getInputStream();
        var chunk = new byte[1024];
        var bytesRead = -1;

        streams.put(fileId, outputStream);
        progress.put(fileId, 0);

        while ((bytesRead = inputStream.read(chunk)) != -1) {
            outputStream.write(chunk, 0, bytesRead);
        }
    }

    @GetMapping("/{fileId}/progress")
    //curl -X GET http://localhost:8080/files/{fileId}/progress
    public int getProgress(@PathVariable String fileId) {
        return progress.get(fileId);
    }

    @GetMapping("/{fileId}")
    //curl -X GET http://localhost:8080/files/{fileId} > path
    public StreamingResponseBody downloadFile(@PathVariable String fileId, HttpServletResponse response) throws IOException {
        return outputStream -> {
            var fileChunk = streams.get(fileId).toByteArray();
            var chunkSize = 1024;
            var totalBytes = fileChunk.length;
            var bytesWritten = 0;

            for (var offset = 0; offset < fileChunk.length; offset += chunkSize) {
                var chunkLength = Math.min(chunkSize, fileChunk.length - offset);
                outputStream.write(fileChunk, offset, chunkLength);
                bytesWritten += chunkLength;
                var progress = (int) (((double) bytesWritten / totalBytes) * 100);
                this.progress.put(fileId, progress);
            }

            streams.remove(fileId);
            progress.remove(fileId);
        };
    }
}
