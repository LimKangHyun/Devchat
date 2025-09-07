package project.backend.domain.member.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.security.crypto.password.PasswordEncoder;
import project.backend.auth.dto.MemberDetails;
import project.backend.domain.chat.chatroom.entity.ChatParticipant;
import project.backend.domain.member.friend.entity.Friends;

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
	private String username;

	@Column(nullable = false)
	private String nickname;

	@Column(unique = true)
	private String email;

	private String password;

	@Column(updatable = false)
	@Enumerated(EnumType.STRING)
	private ProviderType provider;

	@Builder.Default
	private LocalDateTime joinAt = LocalDateTime.now();

	@Builder.Default
	@OneToMany(mappedBy = "participant")
	private List<ChatParticipant> participants = new ArrayList<>();

	@Builder.Default
	@OneToMany(mappedBy = "owner", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<Friends> friends = new ArrayList<>();

	@Column(nullable = false)
	private String profileImage;

	@Setter
	private Long recentRoomId;

	public static Member of(MemberDetails memberDetails) {
		return Member.builder()
			.id(memberDetails.getId())
			.username(memberDetails.getUsername())
			.email(memberDetails.getEmail())
			.password(memberDetails.getPassword())
			.nickname(memberDetails.getNickname())
			.provider(memberDetails.getProvider())
			.profileImage(memberDetails.getProfileImg())
			.build();
	}

	public void updateNickname(String nickname) {
		this.nickname = nickname;

	}

	public void updateEmail(String email) {
		this.email = email;
	}

	public void updatePassword(String password, PasswordEncoder encoder) {
		this.password = encoder.encode(password);
	}

	public void updateProfileImage(String profileImage) {
		this.profileImage = profileImage;
	}

	public void addFriend(Friends friend) {
		this.friends.add(friend);
	}
}
