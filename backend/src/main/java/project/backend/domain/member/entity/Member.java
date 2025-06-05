package project.backend.domain.member.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import project.backend.domain.chat.chatroom.entity.ChatParticipant;
import project.backend.domain.imagefile.ImageFile;

@Entity
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Member {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "member_id")
	private Long id;

	@Column(nullable = false, unique = true)
	private String email;

	@Setter
	private String password;

	@Column(updatable = false, name = "github_username")
	private String gitHubUserName;

	@Column(updatable = false)
	@Enumerated(EnumType.STRING)
	private ProviderType provider;

	@Setter
	@Column(nullable = false)
	private String nickname;

	@Builder.Default
	private LocalDateTime joinAt = LocalDateTime.now();

	@Builder.Default
	@OneToMany(mappedBy = "participant")
	private List<ChatParticipant> participants = new ArrayList<>();

	@Setter
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "profile_image_id")
	private ImageFile profileImage;

	@Setter
	private Long recentRoomId;
}
