package diary.capstone.domain.feed

import org.bson.types.ObjectId
import org.springframework.data.mongodb.repository.MongoRepository

interface FeedRepositoryM: MongoRepository<FeedM, ObjectId> {
}