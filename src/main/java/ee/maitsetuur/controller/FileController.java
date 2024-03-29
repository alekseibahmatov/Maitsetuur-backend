package ee.maitsetuur.controller;

import ee.maitsetuur.model.file.FileType;
import ee.maitsetuur.service.miscellaneous.StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("${api.basepath}/file")
@RequiredArgsConstructor
public class FileController {

    private final StorageService service;

    @GetMapping("/download/{fileId}")
    public ResponseEntity<Resource> downloadRestaurantFile(@PathVariable String fileId) throws Exception {
        Map<String, Object> fileMap = service.downloadRestaurantFile(fileId);
        Resource file = (Resource) fileMap.get("file");
        FileType fileType = (FileType) fileMap.get("fileType");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentDispositionFormData("attachment", file.getFilename());
        headers.setCacheControl(CacheControl.noCache().getHeaderValue());
        headers.setPragma("no-cache");
        headers.setExpires(0);

        MediaType mediaType = fileType == FileType.PHOTO ? MediaType.IMAGE_PNG : MediaType.APPLICATION_PDF;
        long contentLength = file.contentLength();

        return ResponseEntity.ok()
                .headers(headers)
                .contentType(mediaType)
                .contentLength(contentLength)
                .body(file);
    }
}
