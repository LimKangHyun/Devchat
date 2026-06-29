package project.backend.domain.community.app;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import project.backend.auth.dto.MemberDetails;
import project.backend.domain.chat.chatroom.dao.ChatRoomRepository;
import project.backend.domain.chat.chatroom.entity.ChatRoom;
import project.backend.domain.community.dao.PostRepository;
import project.backend.domain.community.dto.PostCreateRequest;
import project.backend.domain.community.dto.PostResponse;
import project.backend.domain.community.dto.PostUpdateRequest;
import project.backend.domain.community.entity.Post;
import project.backend.domain.community.mapper.CommunityMapper;
import project.backend.domain.member.entity.Member;
import project.backend.global.exception.errorcode.ChatRoomErrorCode;
import project.backend.global.exception.errorcode.PostErrorCode;
import project.backend.global.exception.ex.ChatRoomException;
import project.backend.global.exception.ex.PostException;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostService {

    private final PostRepository postRepository;
    private final ChatRoomRepository chatRoomRepository;

    public Slice<PostResponse> getPosts(String sort, boolean activeOnly, boolean myApplied,
                                        int page, int size, MemberDetails memberDetails) {
        PageRequest pageable = PageRequest.of(page, size);

        if (myApplied) {
            if (memberDetails == null) {
                throw new PostException(PostErrorCode.POST_FORBIDDEN);
            }
            return postRepository
                    .findAppliedByMember(memberDetails.getId(), pageable)
                    .map(PostResponse::from);
        }

        Slice<Post> posts;
        if (activeOnly) {
            LocalDate today = LocalDate.now();
            posts = sort.equals("hot")
                    ? postRepository.findActiveByOrderByViewCountDesc(pageable, today)
                    : postRepository.findActiveByOrderByCreatedAtDesc(pageable, today);
        } else {
            posts = sort.equals("hot")
                    ? postRepository.findAllByOrderByViewCountDesc(pageable)
                    : postRepository.findAllByOrderByCreatedAtDesc(pageable);
        }

        return posts.map(PostResponse::from);
    }

    @Transactional
    public PostResponse getPost(Long postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new PostException(PostErrorCode.POST_NOT_FOUND));
        post.incrementViewCount();
        return PostResponse.from(post);
    }

    @Transactional
    public PostResponse createPost(PostCreateRequest request, MemberDetails memberDetails) {
        Member author = Member.of(memberDetails);

        if (postRepository.existsByChatRoom_Id(request.getChatRoomId())) {
            throw new PostException(PostErrorCode.CHATROOM_ALREADY_HAS_POST);
        }

        ChatRoom chatRoom = chatRoomRepository.findById(request.getChatRoomId())
                .orElseThrow(() -> new ChatRoomException(ChatRoomErrorCode.CHATROOM_NOT_FOUND));

        return PostResponse.from(
                postRepository.save(CommunityMapper.toPost(request, author, chatRoom)));
    }

    @Transactional
    public PostResponse updatePost(Long postId, PostUpdateRequest request,
                                   MemberDetails memberDetails) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new PostException(PostErrorCode.POST_NOT_FOUND));

        if (!post.getAuthor().getId().equals(memberDetails.getId())) {
            throw new PostException(PostErrorCode.POST_FORBIDDEN);
        }
        if (post.isClosed()) {
            throw new PostException(PostErrorCode.POST_ALREADY_CLOSED);
        }
        if (request.getMaxCount() != null && request.getMaxCount() < post.getCurrentCount()) {
            throw new PostException(PostErrorCode.POST_MAX_COUNT_INVALID);
        }

        post.update(
                request.getTitle(),
                request.getContent(),
                request.getMaxCount(),
                request.getTag(),
                request.getMode(),
                request.getDeadline(),
                request.getTechStacks() != null ? String.join(",", request.getTechStacks()) : null
        );

        return PostResponse.from(post);
    }

    @Transactional
    public void deletePost(Long postId, MemberDetails memberDetails) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new PostException(PostErrorCode.POST_NOT_FOUND));

        if (!post.getAuthor().getId().equals(memberDetails.getId())) {
            throw new PostException(PostErrorCode.POST_FORBIDDEN);
        }

        postRepository.delete(post);
    }
}