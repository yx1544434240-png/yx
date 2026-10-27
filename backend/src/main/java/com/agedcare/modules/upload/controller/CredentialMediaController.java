package com.agedcare.modules.upload.controller;

import com.agedcare.common.storage.CredentialStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class CredentialMediaController {
    private final CredentialStorage storage;
    @GetMapping("/media/private/credentials/{ownerId}/{filename}")
    public ResponseEntity<Resource> read(@PathVariable Long ownerId, @PathVariable String filename) {
        Resource file = storage.read(ownerId, filename);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("X-Content-Type-Options", "nosniff")
                .contentType(MediaTypeFactory.getMediaType(filename).orElse(MediaType.APPLICATION_OCTET_STREAM)).body(file);
    }
}
