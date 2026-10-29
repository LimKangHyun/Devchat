package project.api.domain.member.friend.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FriendRowTest {

	@Test
	void toResponse에_친구_회원_id가_담긴다() {
		FriendRow row = new FriendRow(7L, "friend", "친구", "img.png");

		FriendResponse response = row.toResponse("ONLINE");

		assertThat(response.id()).isEqualTo(7L);
		assertThat(response.username()).isEqualTo("friend");
		assertThat(response.nickname()).isEqualTo("친구");
		assertThat(response.status()).isEqualTo("ONLINE");
		assertThat(response.profileImage()).isEqualTo("img.png");
	}
}
