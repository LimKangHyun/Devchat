package project.api.domain.github.dto;

import lombok.Builder;
import lombok.Getter;
import project.api.domain.chat.chatroom.entity.ChatRoom;
import project.api.domain.aireview.entity.PrStatus;

@Getter
@Builder
public class GitMessageDto {

	private GitEventType type;
	private PrStatus prStatus;
	private String actor;
	private String content;
	private String fullContent;
	private Integer prNumber;
	private ChatRoom room;

	public GitMessageDto withRoom(ChatRoom room) {
		return GitMessageDto.builder()
				.type(this.type)
				.prStatus(this.prStatus)
				.actor(this.actor)
				.content(this.content)
				.fullContent(this.fullContent)
				.prNumber(this.prNumber)
				.room(room)
				.build();
	}

	public void updateContent(String content) {
		this.content = content;
	}
}