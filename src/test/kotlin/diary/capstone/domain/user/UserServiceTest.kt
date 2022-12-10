package diary.capstone.domain.user

import org.junit.jupiter.api.Test

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@Transactional
internal class UserServiceTest {

    @Autowired lateinit var userService: UserService
    @Autowired lateinit var userRepository: UserRepository
    @Autowired lateinit var qUserRepository: QUserRepository

    /**
     * Follow 엔티티로부터 user 또는 target 엔티티 페치 조인
     * - 유저 페이징 목록: 2(페이징) + 1(following) = 3개 쿼리 실행
     */
    private fun userTestResult(userList: Page<UserSimpleResponse>) {
        println("totalSize: ${userList.totalElements}")
        userList.content.forEach {
            println("${it.id} User ================================")
            println("name: ${it.name}")
            println("image: ${it.image?.source}")
            println("isFollowed: ${it.isFollowed}")
        }
    }

    @Test @DisplayName("userId 로 유저 조회")
    fun getUserById() {
        val userId = 6L
        val loginUser = userService.getUserById(1L)
        val result = qUserRepository.findUserDetailByUserId(userId, loginUser)

        // user, follow, 로그인 유저 following -> 3개 쿼리 실행
        println("[${result.id}] ${result.name}")
        println("followingCount = ${result.followingCount}")
        println("followerCount = ${result.followerCount}")
        println("image = ${result.image?.source}")
        println("occupation = ${result.occupation}")
        println("interests = ${result.interests}")
        println("isFollowed = ${result.isFollowed}")
    }

    @Test @DisplayName("이메일 또는 이름으로 유저 검색")
    fun searchUser() {
        val loginUser = userService.getUserById(1L)
        val pageable = PageRequest.of(0, 10)
        val result = qUserRepository.findUsersByEmailOrName(pageable, "이", loginUser)

        userTestResult(result)
    }

    @Test @DisplayName("유저 Id 목록을 주면 UserSimpleResponse 목록(isFollowed 포함)을 반환")
    fun isFollowing() {
        val userId = 6L
        val writersIds = listOf(1L, 2L, 3L, 4L, 5L, 7L, 10L)
        val users = qUserRepository.getUsersAndIsFollowed(userId, writersIds)
        users.forEach { println(it) }
    }

    @Test @DisplayName("해당 유저의 팔로잉 유저 페이징 목록")
    fun getFollowing() {
        val loginUser = userService.getUserById(1L)
        val pageable = PageRequest.of(0, 10)
        val result = qUserRepository.findFollowingByUserId(pageable, loginUser.id!!, loginUser)

        userTestResult(result)
    }

    @Test @DisplayName("해당 유저의 팔로워 페이징 목록")
    fun getFollowers() {
        val loginUser = userService.getUserById(1L)
        val pageable = PageRequest.of(0, 10)
        val result = qUserRepository.findFollowersByUserId(pageable, loginUser.id!!, loginUser)

        userTestResult(result)
    }
}