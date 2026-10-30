package project.api.domain.member.app;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AdminMembersTest {

    @Test
    void 적어_둔_회원_번호만_관리자다() {
        AdminMembers admins = new AdminMembers("1, 7 ,12");

        assertThat(admins.isAdmin(1L)).isTrue();
        assertThat(admins.isAdmin(7L)).isTrue();
        assertThat(admins.isAdmin(12L)).isTrue();
        assertThat(admins.isAdmin(2L)).isFalse();
    }

    @Test
    void 비어_있으면_아무도_관리자가_아니다() {
        assertThat(new AdminMembers("").isAdmin(1L)).isFalse();
        assertThat(new AdminMembers(null).isAdmin(1L)).isFalse();
    }

    @Test
    void 숫자가_아닌_값은_건너뛴다() {
        AdminMembers admins = new AdminMembers("abc,3,,");

        assertThat(admins.isAdmin(3L)).isTrue();
    }

    @Test
    void 회원_번호가_없으면_관리자가_아니다() {
        assertThat(new AdminMembers("1").isAdmin(null)).isFalse();
    }
}
