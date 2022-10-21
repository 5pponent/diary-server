package diary.capstone.config

/**
 * 미디어 파일 저장 경로 (테스트/운영)
 * EC2 스토리지 -> S3 로 이전, 현재 저장 경로 미사용
 */
//const val FILE_SAVE_PATH = "C:\\diary\\"
//const val FILE_SAVE_PATH = "///home/ubuntu/diary-server/file/"

// 정적 파일 요청 루트 URL
//const val RESOURCE_URL = "https://di4ry.com/resource/"

// 토큰 유효 시간
const val TOKEN_VALID_TIME = 180 * 1000 * 60 // 180분

// 관심 분야 최대 개수
const val INTERESTS_LIMIT = 3

// 페이지당 표시 할 데이터 개수
const val FEED_PAGE_SIZE = 10
const val COMMENT_PAGE_SIZE = 10

// 정적 리소스 캐싱 시간(분)
const val CACHING_MINUTES: Long = 5

// 인증 코드 설정
const val AUTH_CODE_CHARS = "0123456789"  // 인증 코드에 사용할 문자열들
const val AUTH_CODE_DIGITS = 6            // 인증 코드의 자릿수
const val AUTH_CODE_VALID_MINUTE = 5      // 인증 코드 유효 시간(분)
