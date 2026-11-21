package com.just.critter.interaction.controller;

import com.just.critter.interaction.service.CommentCreateCommand;
import com.just.critter.interaction.service.CommentReplyCommand;
import com.just.critter.interaction.service.CommentService;
import com.just.critter.interaction.service.CommentView;
import com.just.critter.interaction.service.FollowCommand;
import com.just.critter.interaction.service.FollowRemoval;
import com.just.critter.interaction.service.FollowService;
import com.just.critter.interaction.service.FollowView;
import com.just.critter.support.api.PageQuery;
import com.just.critter.support.api.PageResult;
import com.just.critter.support.api.Result;
import com.just.critter.support.security.AuthContext;
import com.just.critter.support.security.CurrentUser;
import com.just.critter.support.exception.BusinessException;
import com.just.critter.support.exception.ErrorCode;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 留言与关注，接口编号 35–39。
 * /comments 走可选鉴权：匿名可读公开留言，登录后额外能看到自己的待审留言。
 */
@RestController
@RequestMapping("/api/v1")
public class InteractionController {

    private final CommentService comments;
    private final FollowService follows;

    public InteractionController(CommentService comments, FollowService follows) {
        this.comments = comments;
        this.follows = follows;
    }

    @PostMapping("/comments")
    public Result<CommentView> createComment(@RequestBody CommentCreateRequest request) {
        return Result.ok(comments.create(new CommentCreateCommand(
                request.bizType(), request.bizId(), request.content()), AuthContext.require().id()));
    }

    @GetMapping("/comments")
    public Result<PageResult<CommentView>> listComments(
            @RequestParam String bizType,
            @RequestParam String bizId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        CurrentUser current = AuthContext.get();
        Long viewerId = current == null ? null : current.id();
        boolean admin = current != null && (current.isAdmin() || current.isSystemAdmin());
        return Result.ok(comments.list(bizType, bizId, new PageQuery(page, size), viewerId, admin));
    }

    @PutMapping("/comments/{id}/reply")
    public Result<CommentView> replyComment(@PathVariable long id,
                                            @RequestBody CommentReplyRequest request) {
        CurrentUser current = AuthContext.require();
        if (!(current.isAdmin() || current.isSystemAdmin())) {
            throw new BusinessException(ErrorCode.ROLE_MISMATCH);
        }
        return Result.ok(comments.reply(id,
                new CommentReplyCommand(request.replyContent(), request.visible())));
    }

    @PostMapping("/follows")
    public Result<FollowView> follow(@RequestBody FollowRequest request) {
        return Result.ok(follows.follow(new FollowCommand(
                request.bizType(), request.bizId()), AuthContext.require().id()));
    }

    @DeleteMapping("/follows/{id}")
    public Result<FollowRemoval> unfollow(@PathVariable long id) {
        return Result.ok(follows.unfollow(id, AuthContext.require().id()));
    }
}
