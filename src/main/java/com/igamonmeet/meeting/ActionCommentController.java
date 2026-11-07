package com.igamonmeet.meeting;

import com.igamonmeet.template.TeamTag;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ActionCommentController {

    public record NewAction(@NotBlank @Size(max = 500) String title, @NotBlank @Size(max = 200) String owner,
                            @NotNull TeamTag teamTag, LocalDate dueDate) {
    }

    public record EditAction(long version, @NotBlank @Size(max = 500) String title, @NotBlank @Size(max = 200) String owner,
                             @NotNull TeamTag teamTag, LocalDate dueDate, @NotBlank String status) {
    }

    public record NewComment(@NotBlank String sectionKey, Long parentId, @NotBlank @Size(max = 2000) String body) {
    }

    public record CommentView(long id, String author, String body, Instant createdAt) {
    }

    public record Thread(long id, String sectionKey, String author, String body, Instant createdAt, boolean resolved,
                         String resolvedBy, Instant resolvedAt, List<CommentView> replies) {
    }

    private final ActionsAndComments service;
    private final CommentRepository comments;

    public ActionCommentController(ActionsAndComments service, CommentRepository comments) {
        this.service = service;
        this.comments = comments;
    }

    @PostMapping("/api/meetings/{id}/actions")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Long> add(@PathVariable long id, @Valid @RequestBody NewAction r, Authentication auth) {
        return Map.of("id", service.add(id, r.title(), r.owner(), r.teamTag(), r.dueDate(), MeetingController.isAdmin(auth)).getId());
    }

    @PutMapping("/api/meetings/{id}/actions/{actionId}")
    public Map<String, Long> update(@PathVariable long id, @PathVariable long actionId, @Valid @RequestBody EditAction r, Authentication auth) {
        ActionItem a = service.update(id, actionId, r.version(), r.title(), r.owner(), r.teamTag(), r.dueDate(), r.status(), MeetingController.isAdmin(auth));
        return Map.of("version", a.getVersion());
    }

    @DeleteMapping("/api/meetings/{id}/actions/{actionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable long id, @PathVariable long actionId, Authentication auth) {
        service.remove(id, actionId, MeetingController.isAdmin(auth));
    }

    @GetMapping("/api/meetings/{id}/comments")
    public List<Thread> threads(@PathVariable long id) {
        List<Comment> all = comments.findByMeetingIdOrderByCreatedAtAscIdAsc(id);
        return all.stream().filter(c -> c.getParentId() == null)
                .map(root -> new Thread(root.getId(), root.getSectionKey(), root.getAuthor(), root.getBody(), root.getCreatedAt(),
                        root.isResolved(), root.getResolvedBy(), root.getResolvedAt(),
                        all.stream().filter(c -> root.getId().equals(c.getParentId()))
                                .map(c -> new CommentView(c.getId(), c.getAuthor(), c.getBody(), c.getCreatedAt())).collect(Collectors.toList())))
                .collect(Collectors.toList());
    }

    @PostMapping("/api/meetings/{id}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Long> comment(@PathVariable long id, @Valid @RequestBody NewComment r, Authentication auth) {
        return Map.of("id", service.comment(id, r.sectionKey(), r.parentId(), r.body(), auth.getName()).getId());
    }

    @PostMapping("/api/meetings/{id}/comments/{commentId}/resolve")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resolve(@PathVariable long id, @PathVariable long commentId, Authentication auth) {
        service.resolve(id, commentId, true, auth.getName());
    }

    @PostMapping("/api/meetings/{id}/comments/{commentId}/reopen")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reopen(@PathVariable long id, @PathVariable long commentId, Authentication auth) {
        service.resolve(id, commentId, false, auth.getName());
    }
}
