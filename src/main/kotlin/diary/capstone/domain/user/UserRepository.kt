package diary.capstone.domain.user

import com.querydsl.core.types.Predicate
import com.querydsl.jpa.impl.JPAQueryFactory
import diary.capstone.domain.user.QFollow.follow
import diary.capstone.domain.user.QUser.user
import diary.capstone.util.getPagedObject
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

interface UserRepository: JpaRepository<User, Long> {

    fun findByEmail(email: String): User?
    fun existsByEmail(email: String): Boolean

    /**
     * 검색 정책
     * - 이름: 입력받은 문자열을 포함하는 모든 유저
     * - 이메일: 입력받은 문자열을 맨 앞에 포함하는 모든 유저
     * @return 유저 페이징 목록
     */
    @Query("SELECT u FROM User u WHERE u.name LIKE %:name% OR u.email LIKE :email%")
    fun searchAllByNameOrEmail(
        pageable: Pageable,
        @Param("name") name: String,
        @Param("email") email: String
    ): Page<User>
}

@Repository
class QUserRepository(private val jpaQueryFactory: JPAQueryFactory) {

    fun findByEmail(email: String): User? = jpaQueryFactory.selectFrom(user)
        .leftJoin(user.profileImage).fetchJoin()
        .leftJoin(user.occupation).fetchJoin()
        .where(user.email.eq(email))
        .fetchOne()

    /**
     * @return userId가 targetUserId를 팔로우중인지 여부
     */
    fun getIsFollowed(userId: Long, targetUserId: Long): Boolean = jpaQueryFactory.selectFrom(follow)
        .where(follow.user.id.eq(userId), follow.target.id.eq(targetUserId))
        .fetchOne()?.let { true } ?: false

    /**
     * @return targetUserIds의 유저들 중 팔로우중인 유저 ID만 담긴 List 반환
     */
    fun getIsFollowed(userId: Long, targetUserIds: List<Long>): List<Long> =
        jpaQueryFactory.selectFrom(follow)
            .where(follow.user.id.eq(userId), follow.target.id.`in`(targetUserIds)).fetch()
            .map { it.target.id!! }.toList()

    /**
     * @param userId 대상 유저 ID
     * @param targetUserIds 대상 유저가 팔로우 했는지를 확인하기 위한 유저 ID 리스트
     */
    fun getUsersAndIsFollowed(userId: Long, targetUserIds: List<Long>): List<UserSimpleResponse> {
        val users = jpaQueryFactory
            .selectFrom(user)
            .leftJoin(user.profileImage).fetchJoin()
            .where(user.id.`in`(targetUserIds))
            .fetch()
        val follows = jpaQueryFactory
            .select(follow.target.id)
            .from(follow)
            .where(follow.user.id.eq(userId).and(follow.target.id.`in`(targetUserIds)))
            .fetch()
        val followedMap = targetUserIds.associateWith { follows.contains(it) }
        return users.map { UserSimpleResponse(it, followedMap[it.id]!!) }
    }

    /**
     * JOIN FETCH: target, target.profileImage
     * @return 해당 유저가 팔로우중인 유저 페이징 목록, target.name ASC
     */
    fun findFollowingByUserId(pageable: Pageable, userId: Long, loginUser: User): Page<UserSimpleResponse> {
        val expr = follow.user.id.eq(userId)
        val followings = jpaQueryFactory
            .selectFrom(follow).distinct()
            .leftJoin(follow.target, user).fetchJoin()
            .leftJoin(user.profileImage).fetchJoin()
            .where(expr)
            .orderBy(user.name.asc())
            .offset(pageable.offset)
            .limit(pageable.pageSize.toLong())
            .fetch()
            .map { UserSimpleResponse(it.target, loginUser) }

        return getPagedObject(pageable, followings, getFollowCount(expr))
    }

    /**
     * JOIN FETCH: user, user.profileImage
     * @return 해당 유저의 팔로워 페이징 목록, user.name ASC
     */
    fun findFollowersByUserId(pageable: Pageable, userId: Long, loginUser: User): Page<UserSimpleResponse> {
        val expr = follow.target.id.eq(userId)
        val followers = jpaQueryFactory
            .selectFrom(follow).distinct()
            .leftJoin(follow.user, user).fetchJoin()
            .leftJoin(user.profileImage).fetchJoin()
            .where(expr)
            .orderBy(user.name.asc())
            .offset(pageable.offset)
            .limit(pageable.pageSize.toLong())
            .fetch()
            .map { UserSimpleResponse(it.user, loginUser) }

        return getPagedObject(pageable, followers, getFollowCount(expr))
    }

    /**
     * JOIN FETCH: user.profileImage
     * @return 이메일 keyword% or 이름 %keyword% 일치하는 유저 페이징 목록, user.id ASC
     */
    fun findUsersByEmailOrName(pageable: Pageable, keyword: String, loginUser: User): Page<UserSimpleResponse> {
        val expr = user.email.like("$keyword%").or(user.name.like("%$keyword%"))
        val users = jpaQueryFactory
            .selectFrom(user)
            .leftJoin(user.profileImage).fetchJoin()
            .where(expr)
            .offset(pageable.offset)
            .limit(pageable.pageSize.toLong())
            .fetch()

        val totalSize = jpaQueryFactory
            .select(user.count()).from(user).where(expr).fetchOne()!!

        return getPagedObject(pageable, users.map { UserSimpleResponse(it, loginUser) }, totalSize)
    }

    /**
     * JOIN FETCH: occupation, profileImage
     * @return 해당 유저의 UserDetailResponse
     */
    fun findUserDetailByUserId(userId: Long, loginUser: User): UserDetailResponse {
        val findUser = jpaQueryFactory
            .selectFrom(user)
            .leftJoin(user.occupation).fetchJoin()
            .leftJoin(user.profileImage).fetchJoin()
            .where(user.id.eq(userId))
            .fetchOne() ?: throw UserException(USER_NOT_FOUND)

        val findFollow = jpaQueryFactory
            .selectFrom(follow)
            .leftJoin(follow.user, user).fetchJoin()
            .where(follow.user.id.eq(userId).or(follow.target.id.eq(userId)))
            .fetch()

        val followingCount = findFollow.count { it.user.id == userId }
        val followerCount = findFollow.count() - followingCount

        return UserDetailResponse(findUser, loginUser, followingCount, followerCount)
    }

    private fun getFollowCount(expr: Predicate): Long = jpaQueryFactory
        .select(follow.count()).from(follow).where(expr).fetchOne()!!
}