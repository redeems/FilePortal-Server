package com.fileportal.portal;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The FileController class provides efficient file transfer between two clients by storing the input stream of an uploaded
 * file in a map and transferring the data in chunks using the transferTo method. This allows for multiple large files to
 * be transferred simultaneously without requiring a database or excessive memory usage on the server.
 */
@RestController
@RequestMapping("/files")
public class FileController {
    private record Request(Semaphore semaphore, InputStream stream, long contentLength, String fileName, AtomicBoolean accessed, long created) { }
    private static final Logger LOGGER = Logger.getLogger(FileController.class.getName());
    private final Map<String, Request> streams = new ConcurrentHashMap<>();
    private static final long REQUEST_CLEAR_DELAY = 1; //minutes
    private static final long REQUEST_TIMEOUT = 10; //minutes

    @Scheduled(fixedDelay = REQUEST_CLEAR_DELAY * 60 * 1000)
    public void clearStaleRequests() {
        streams.forEach((key, request) -> {
            if (!request.accessed.get() && System.currentTimeMillis() - request.created >= REQUEST_TIMEOUT * 60 * 1000) {
                LOGGER.info("removing request: " + request.fileName);
                streams.remove(key);
            }
        });
    }

    /**
     * Accepts an input stream and stores it in a map to be retrieved later by a downloading client. The uploaded file's
     * input stream, content length, and file name are stored in a Request record and placed in the map under the specified
     * fileId. A semaphore is acquired and released to synchronize the upload and download processes.
     *
     * @param fileId  a unique identifier for the uploaded file
     * @param request the HttpServletRequest containing the input stream of the file being uploaded and the "Name" header
     *                specifying the file name
     *                <br><br>
     *                fails if an error occurs reading the input stream
     *                <br>
     *                fails if the thread is interrupted while acquiring the semaphore
     */
    @PutMapping("/{fileId}")
    //curl -v -X PUT -H "Content-Type: application/octet-stream" -H "Name: filename" --data-binary "@path" http://localhost:8080/files/{fileId}
    public void uploadFile(@PathVariable String fileId, HttpServletRequest request) {
        try {
            var semaphore = new Semaphore(0);
            var fileName = request.getHeader("Name");
            var fileSize = request.getContentLength();
            var r = new Request(semaphore, request.getInputStream(), fileSize, fileName, new AtomicBoolean(false), System.currentTimeMillis());
            streams.put(fileId, r);
            semaphore.acquire();
        } catch (IOException | InterruptedException e) {

        }
    }

    /**
     * Retrieves the stored input stream for the specified file and transfers the data to the provided response's output stream
     * in chunks using the transferTo method. The semaphore associated with the Request record is released to indicate that
     * the download process is complete.
     *
     * @param fileId   the unique identifier for the file to be downloaded
     * @param response the HttpServletResponse to transfer the file data to
     * @throws FileDownloadException if the request for a given file id does not exist.
     */
    @GetMapping("/{fileId}")
    //curl -X GET http://localhost:8080/files/{fileId} > path
    public void downloadFile(@PathVariable String fileId, HttpServletResponse response) throws FileDownloadException {
        try {
            var request = streams.get(fileId);

            if (request == null) {
                LOGGER.log(Level.SEVERE, "File with ID " + fileId + " not found");
                throw new FileNotFoundException("");
            }

            LOGGER.info("downloading: [" + fileId + "] " + request.fileName + " [" + request.contentLength + " bytes]");
            response.setHeader("Content-Disposition", "attachment; filename=" + request.fileName);
            streams.get(fileId).accessed.set(true);
            streams.get(fileId).stream.transferTo(response.getOutputStream());
            streams.get(fileId).semaphore.release();
            streams.remove(fileId);
            LOGGER.info("done with: [" + fileId + "]");
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "File with ID " + fileId + " failed to download.");
            throw new FileDownloadException("");
        }
    }
}
