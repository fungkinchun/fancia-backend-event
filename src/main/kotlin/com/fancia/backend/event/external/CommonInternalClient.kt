package com.fancia.backend.event.external

import com.fancia.backend.event.config.FeignConfig
import com.fancia.backend.shared.common.comment.core.dto.CommentResponse
import com.fancia.backend.shared.common.comment.core.dto.CreateCommentRequest
import com.fancia.backend.shared.common.post.core.dto.CreatePostRequest
import com.fancia.backend.shared.common.post.core.dto.PostResponse
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import java.util.UUID

@FeignClient(
    name = "common-internal-service",
    path = "/internal",
    configuration = [FeignConfig::class],
)
interface CommonInternalClient {
    @PostMapping("/comments")
    fun createComment(@RequestBody request: CreateCommentRequest): CommentResponse

    @GetMapping("/comments")
    fun listComments(
        @RequestParam(required = false) targetId: UUID?,
        @RequestParam(required = false) postId: UUID?,
        pageable: Pageable,
    ): Page<CommentResponse>

    @PostMapping("/posts")
    fun createPost(@RequestBody request: CreatePostRequest): PostResponse

    @GetMapping("/posts")
    fun listPosts(
        @RequestParam targetId: UUID,
        pageable: Pageable,
    ): Page<PostResponse>

    @GetMapping("/posts/{postId}")
    fun getPost(@PathVariable postId: UUID): PostResponse
}
