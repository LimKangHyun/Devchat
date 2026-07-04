package project.api.domain.chat.chatroom.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import java.io.Serializable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@NoArgsConstructor
@Getter
public class ChatRoomAlarm {

	@EmbeddedId
	private ChatRoomAlarmId id;

	@Column(nullable = false)
	@Setter
	private boolean enabled = true; //기본값: 알림 켜짐

	@Embeddable
	@EqualsAndHashCode
	@AllArgsConstructor
	@NoArgsConstructor(access = AccessLevel.PROTECTED)
	public static class ChatRoomAlarmId implements Serializable {

		private Long memberId;
		private Long roomId;
	}

	public ChatRoomAlarm(Long memberId, Long roomId) {
		this.id = new ChatRoomAlarmId(memberId, roomId);
		this.enabled = true; // 명시적으로도 정의
	}

}
