package com.memolake.interfaces.rest;

import com.memolake.application.MemoService;
import com.memolake.application.dto.MemoDetail;
import com.memolake.application.dto.MemoWriteResult;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Memo endpoints. Write requests take the raw memo text as a {@code text/plain} body.
 *
 * <ul>
 *   <li>POST  - create</li>
 *   <li>PATCH - replace the whole content (optional {@code If-Match} version check)</li>
 *   <li>PUT   - append to the end of the content. Not idempotent: a retried PUT appends again.
 *       Send {@code If-Match} to make a retry fail with 412 instead.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/topic/{topicName}/{memoName}")
public class MemoController {

    private final MemoService memoService;
    private final PlainTextBodyReader bodyReader;

    public MemoController(MemoService memoService, PlainTextBodyReader bodyReader) {
        this.memoService = memoService;
        this.bodyReader = bodyReader;
    }

    @GetMapping
    public ResponseEntity<MemoDetail> getMemo(@PathVariable String topicName, @PathVariable String memoName) {
        MemoDetail memo = memoService.getMemo(topicName, memoName);
        return ResponseEntity.ok().eTag(VersionETags.of(memo.version())).body(memo);
    }

    @PostMapping(consumes = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<MemoWriteResult> createMemo(@PathVariable String topicName, @PathVariable String memoName,
                                                      HttpServletRequest request) {
        MemoWriteResult result = memoService.createMemo(topicName, memoName, bodyReader.read(request));
        var location = UriComponentsBuilder.fromPath("/api/topic/{topicName}/{memoName}").encode()
                .buildAndExpand(topicName, result.name()).toUri();
        return ResponseEntity.created(location).eTag(VersionETags.of(result.version())).body(result);
    }

    @PatchMapping(consumes = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<MemoWriteResult> replaceMemo(@PathVariable String topicName, @PathVariable String memoName,
                                                       @RequestHeader(name = HttpHeaders.IF_MATCH, required = false)
                                                       String ifMatch,
                                                       HttpServletRequest request) {
        Long expectedVersion = VersionETags.parseIfMatch(ifMatch);
        MemoWriteResult result = memoService.replaceMemo(topicName, memoName, bodyReader.read(request),
                expectedVersion);
        return ResponseEntity.ok().eTag(VersionETags.of(result.version())).body(result);
    }

    @PutMapping(consumes = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<MemoWriteResult> appendMemo(@PathVariable String topicName, @PathVariable String memoName,
                                                      @RequestHeader(name = HttpHeaders.IF_MATCH, required = false)
                                                      String ifMatch,
                                                      HttpServletRequest request) {
        Long expectedVersion = VersionETags.parseIfMatch(ifMatch);
        MemoWriteResult result = memoService.appendMemo(topicName, memoName, bodyReader.read(request),
                expectedVersion);
        return ResponseEntity.ok().eTag(VersionETags.of(result.version())).body(result);
    }
}
