package com.agedcare.modules.upload.controller;

import com.agedcare.common.storage.PrivateMediaService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class PrivateMediaController {
    private final PrivateMediaService media;
    @GetMapping("/media/private/fulfillment/{orderId}/{uploaderId}/{filename}")
    public ResponseEntity<Resource> read(@PathVariable Long orderId, @PathVariable Long uploaderId, @PathVariable String filename) {
        Resource resource = media.read(orderId, uploaderId, filename);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .contentType(MediaTypeFactory.getMediaType(filename).orElse(MediaType.APPLICATION_OCTET_STREAM)).body(resource);
    }
}
